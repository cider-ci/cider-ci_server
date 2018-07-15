(ns cider-ci.utils.jdbc
  (:require
    [clojure.java.jdbc :as jdbc]

    ; upsert! needs this
    [clojure.java.jdbc :as jdbc]
    [clojure.set :as set]
    [clojure.string :as str]
    [honeysql.core :as sql]


    [logbug.catcher :as catcher]
    [clojure.tools.logging :as logging]
    ))

(defn insert-or-update [tx table where-clause values]
  (let [[clause & params] where-clause]
    (if (first (jdbc/query
                 tx
                 (concat [(str "SELECT 1 FROM " table " WHERE " clause)]
                         params)))
      (jdbc/update! tx table values where-clause)
      (jdbc/insert! tx table values))))



; adopted from the gist https://gist.github.com/abhin4v/5a8dace4f308f2eeb358

(defn upsert!
  ([db table uniq-cols row-maps]
   (upsert! db true table uniq-cols row-maps))
  ([db transaction? table uniq-cols row-maps]
   {:pre [(not-empty uniq-cols)]}
   (if (empty? row-maps)
     []
     (let [cols          (keys (first row-maps))
           non-uniq-cols (set/difference (set cols) (set uniq-cols))
           on-conflict   (str "ON CONFLICT (" (str/join ", " uniq-cols) ")")
           qfied         (fn [table col] (->> col name (str (name table) ".") keyword))

           [on-conflict-update] (sql/format
                                  {:set   (->> non-uniq-cols
                                               (mapcat #(vector % (qfied :excluded %)))
                                               (apply hash-map))
                                   :where (list* :and
                                                 (map #(vector := (qfied table %) (qfied :excluded %))
                                                      uniq-cols))})]
       (doall (map (fn [row-map]
                     (let [value (->> row-map seq (sort-by #(->> % first (.indexOf cols))) (map second))
                           [query & params] (sql/format {:insert-into table
                                                         :columns     cols
                                                         :values      [value]})

                           query (if (empty? non-uniq-cols)
                                   (format "%s %s DO NOTHING" query on-conflict)
                                   (format "%s %s DO UPDATE %s" query on-conflict on-conflict-update))]
                       (or (jdbc/db-do-prepared-return-keys db transaction? query params)
                         (let [sel-query (->> {:select [:*] :from [table]
                                               :where  (list* :and
                                                         (map #(vector := (qfied table %) (get row-map %))
                                                              uniq-cols))}
                                              (sql/format))]
                            (jdbc/query db sel-query)))))
                   row-maps))))))

