; Copyright © 2013 - 2018 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.
 
(ns cider-ci.server.projects.repositories.git-sql
  (:refer-clojure :exclude [str keyword parents])
  (:require [cider-ci.utils.core :refer [keyword str presence]])
  (:require
    [cider-ci.server.paths :refer [path]]
    [cider-ci.server.projects.repositories :as repositories]
    [cider-ci.server.projects.repositories.shared :as repositories-shared]
    [cider-ci.utils.git-gpg :as git-gpg]
    [cider-ci.utils.honeysql :as sql]
    [cider-ci.utils.jdbc :as utils.jdbc]
    [cider-ci.utils.rdbms :as ds]

    [clj-time.coerce]
    [clojure.core.async :as async]
    [clojure.java.jdbc :as jdbc]
    [compojure.core :as cpj]

    [clj-logging-config.log4j :as logging-config]
    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug]
    )
  (:import 
    [java.io File]
    [org.eclipse.jgit.api Git]
    [org.eclipse.jgit.lib Repository FileMode]
    [org.eclipse.jgit.revwalk RevCommit RevWalk]
    [org.eclipse.jgit.storage.file FileRepositoryBuilder]
    [org.eclipse.jgit.util RawParseUtils]
    [org.eclipse.jgit.submodule SubmoduleWalk]
    [org.eclipse.jgit.treewalk TreeWalk]
    ))


;;; depths ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:private update-depths-sql
  "UPDATE commits AS children
  SET depth = parents.depth + 1
  FROM commits AS parents,
  commit_arcs
  WHERE children.depth IS NULL
  AND parents.depth IS NOT NULL
  AND children.id = commit_arcs.child_id
  AND parents.id = commit_arcs.parent_id")

(defn- update-depths [tx]
  (loop [ct 0]
    (let [updates (+ ct (first (jdbc/execute! tx [update-depths-sql])))]
      (if (< ct updates)
        (recur updates)
        updates))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;



(defn import-submodule-refs [j-tree-id ^Repository repository]
  (logging/debug 'import-submodule-refs j-tree-id repository)

  ; SubmoduleWalk seems not to help at all
  ; try to do something like in native `git rev-list -r`
  ; see https://github.com/eclipse/jgit/blob/master/org.eclipse.jgit.pgm/src/org/eclipse/jgit/pgm/RevList.java

  ; j-tree-id seems to be some other class, see if this works 
  (let [submodule-walk (doto (SubmoduleWalk. repository)
                         (.setTree j-tree-id))]
    (logging/debug 'submodule-walk submodule-walk)
    (logging/debug 'path (.getPath submodule-walk))
    ;(logging/debug 'modules-path (.getModulesPath submodule-walk))
    (logging/debug 'ObjecetId (.getObjectId submodule-walk))
    (logging/debug 'Resolved (.resolve repository (.getName (.getObjectId submodule-walk))))
    )

  ; might have to iterate over the whole tree and inspect the paths

  ; or not
  ; see SubmoduleWalk and setTree

  ;setTree(AnyObjectId treeId)
  ;Set the tree used for finding submodule entries

  ; https://www.programcreek.com/java-api-examples/?api=org.eclipse.jgit.revwalk.RevTree
  )
 

(defn import-submodule-refs-via-commit [commit ^Repository repository tx]
  (let [tree-walk (TreeWalk. repository)]
    (.addTree tree-walk (.getTree commit))
    (.setRecursive tree-walk true)
    (while (.next tree-walk)
      (when (= (.getFileMode tree-walk 0) FileMode/GITLINK)
        (logging/debug "SUBMODULE: " (.getPathString tree-walk) (.getName (.getObjectId tree-walk 0)))
        (let [commit-id (-> commit .getId .getName)
              path (.getPathString tree-walk)
              submodule-commit-id (.getName (.getObjectId tree-walk 0))]
          (utils.jdbc/insert-or-update 
            tx "submodules"
            ["commit_id = ? AND path = ? AND submodule_commit_id = ?" 
             commit-id path submodule-commit-id]
            {:commit_id commit-id
             :path path
             :submodule_commit_id submodule-commit-id}))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn extract-ident [ident]
  [(.getName ident)
   (.getEmailAddress ident)
   (-> ident .getWhen .getTime clj-time.coerce/from-long)])

(defn- commit-already-persisted? [commit-id & [{tx :tx}]]
  (boolean
    (->> ["SELECT true AS persisted FROM commits WHERE id = ?" commit-id]
         (jdbc/query tx)
         first :persisted)))

(defn import-gpg-signature-and-message [commit tree-id tx]
  (let [ raw-commit (some-> commit .getRawBuffer RawParseUtils/decode)
        gpg-signature (some-> raw-commit git-gpg/extract-ascii-commit-signature)
        gpg-message (some->  raw-commit git-gpg/cat-file-commit-wo-signature)]
    (when (and gpg-signature gpg-message)
      (utils.jdbc/insert-or-update 
        tx "tree_signatures"
        ["tree_id = ? AND message = ? AND signature = ?" 
         tree-id gpg-message gpg-signature]
        {:tree_id tree-id
         :message gpg-message
         :signature gpg-signature}))))

(defn- commit-subject [commit]
  (def ^:dynamic commit* commit)
  (logging/debug (type commit))
  (.getShortMessage commit))

(defn import-commit-new [commit repository rev-walk tx]
  "If the commit is not present in the db import it (as well as import 
  gpg-signature-and-message and submodule references) and return the commit
  itself, return nil otherwise."
  (let [commit-id (-> commit .getId .getName)
        ; ensure we have a parsed commit here, otherwise we get obscure NPEs 
        commit (.parseCommit rev-walk (-> commit .getId))]
    (when-not (commit-already-persisted? commit-id {:tx tx})
      (let [subject (commit-subject commit)
            body (.getFullMessage commit)
            j-tree-id (-> commit .getTree .getId) 
            tree-id (.getName j-tree-id)
            [committer committer-email committer-timestamp] (-> commit .getCommitterIdent extract-ident)
            [author author-email author-timestamp] (-> commit .getAuthorIdent extract-ident)
            prnts (-> commit .getParents seq)
            depth (if prnts nil 0)
            db-commit {:id commit-id            
                       :tree_id tree-id
                       :author_name author
                       :author_email author-email
                       :author_date author-timestamp
                       :committer_name committer
                       :committer_email committer-email
                       :committer_date committer-timestamp
                       :subject subject
                       :body body
                       :depth depth}]
        (jdbc/insert! tx :commits db-commit)
        (import-gpg-signature-and-message commit tree-id tx)
        (import-submodule-refs j-tree-id repository)
        (import-submodule-refs-via-commit commit repository tx)
        commit))))

(defn import-new-arc? [child parent tx]
  (boolean
    (let [child-id (-> child .getId .getName)
          parent-id (-> parent .getId .getName)]
      (when-not (->> ["SELECT true FROM commit_arcs WHERE child_id = ? AND parent_id = ?"
                      child-id parent-id]
                     (jdbc/query tx) first)
        (jdbc/insert! tx :commit_arcs 
                      {:parent_id parent-id 
                       :child_id child-id })))))

(defn import-new-arcs-to-parents [commit rev-walk tx]
  "Find and persist all new arcs to parents. Return a seq of
  parents ^RevCommit for which new arcs have been inserted."
  (->> commit .getParents
       ;(map #(.parseCommit % rev-walk))
       (filter (fn [parent]
                 (import-new-arc?
                   commit parent tx)))))

(defn import-commit-with-parents-new [commit rev-walk repository tx]
  (loop [commits [commit]]
    (when-let [newly-imported-commits (->> commits 
                                           (map (fn [c] 
                                                  (import-commit-new c repository rev-walk tx)))
                                           (filter identity)
                                           seq)]
      (def ^:dynamic newly-imported-commits* newly-imported-commits)
      (when-let [prnts (->> newly-imported-commits
                            (map #(.getParents %))
                            (map #(into [] %))
                            flatten
                            seq)]
        (def ^:dynamic prnts* prnts)
        (recur prnts)))))


(defn- insert-arc!? [child-commit parent-commit tx]
  "If an arc does not alredy exists insert it and return true,
  return false otherwise."
  (let [parent-id (-> parent-commit .getId .getName)
        child-id (-> child-commit .getId .getName)]
    (boolean
      (when-not (->> (-> (sql/select [:true :exists])
                         (sql/from :commit_arcs)
                         (sql/merge-where [:= :child_id child-id])
                         (sql/merge-where [:= :parent_id parent-id])
                         sql/format)
                     (jdbc/query tx)
                     first :exists)
        (jdbc/insert! tx :commit_arcs 
                      {:parent_id parent-id 
                       :child_id child-id})
        true))))

(defn- arcs-to-parents [child]
  (->> (.getParents child)
       (map (fn [parent] [child parent]))
       (into [])))

(defn import-arcs [commit rev-walk tx]
  (loop [arcs (arcs-to-parents commit)]
    (when-let [parent-arcs (some->> arcs
                                    (map (fn [[child parent]] 
                                           (when (insert-arc!? child parent tx)
                                             (arcs-to-parents parent))))
                                    (apply concat)
                                    seq)]
      (recur parent-arcs)))
  (update-depths tx)) 

(defn import-branch [branch repository project tx]
  (let [rev-walk (RevWalk. repository)
        commit (->> branch .getObjectId (.parseCommit rev-walk))]
    (import-commit-with-parents-new commit rev-walk repository tx)
    (import-arcs commit rev-walk tx)
    (let [project-id (:id project)
          branch-name (.getName branch)
          commit-id (-> commit .getId .getName)]
      (utils.jdbc/insert-or-update 
        tx "branches"
        ["project_id = ? AND name = ?" project-id branch-name]
        {:project_id project-id
         :name branch-name
         :current_commit_id commit-id}))))


; TODO will not delete removed branches!
; TODO change to import-or-update-branches 
; see also related code update_branches_commits in the old code
(defn import-branches [project]
  ; jgit sometimes misses newly pushed branches when we do not sleep for a while 
  (async/go 
    (async/<! (async/timeout 1000))
    (catcher/with-logging {}
      (let [repository (:repository project)]
        (assert (instance? Repository repository))
        (jdbc/with-db-transaction [tx @ds/ds]
          (.scanForRepoChanges repository)
          (doseq [branch (some->> repository
                                  Git. .branchList .call seq
                                  (filter #(re-matches #"^refs\/heads\/.*" (.getName %))))]
            (import-branch branch repository project tx)))))))






;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(debug/wrap-with-log-debug #'import-submodule-refs)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'cider-ci.utils.shutdown)
;(debug/debug-ns *ns*)
