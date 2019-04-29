; Copyright © 2013 - 2016 Dr. Thomas Schank <Thomas.Schank@AlgoCon.ch>
; Licensed under the terms of the GNU Affero General Public License v3.
; See the "LICENSE.txt" file provided with this software.
;
(ns cider-ci.utils.pki
  (:require
    [cider-ci.open-session.encoder :as encoder]

    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug]
    [logbug.thrown])
  (:import
    [java.security KeyPairGenerator Security Signature SecureRandom]
    [java.security.spec ECGenParameterSpec]
    [org.bouncycastle.jce.provider BouncyCastleProvider]
    [org.bouncycastle.util.io.pem PemWriter PemObject PemReader]
    [org.bouncycastle.openssl.jcajce JcaPEMWriter JcaPEMKeyConverter]
    [org.bouncycastle.openssl PEMParser]
    [java.io StringWriter StringReader]
    ))

;;; generate key pair ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn generate-key-pair
  "search for 'Supported ECDSA Curves BouncyCastle' for supported curves"
  ([]
   (generate-key-pair "prime256v1"))
  ([curve]
   (Security/addProvider (BouncyCastleProvider.))
   (.generateKeyPair
     (doto (KeyPairGenerator/getInstance "ECDSA", "BC")
       (.initialize (ECGenParameterSpec. curve) (SecureRandom. ))))))

(defn private-key [k]
  (cond
    (instance? java.security.KeyPair k) (.getPrivate k)
    :else k))

(defn public-key [k]
  (cond
    (instance? java.security.KeyPair k) (.getPublic k)
    :else k))


;;; PEM ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- key->pem [k]
  (let [sw (StringWriter.) ]
    (with-open [sw sw
                pw (JcaPEMWriter. sw)]
      (.writeObject pw k))
    (.toString sw)))

(defn key-pair->pem-private [key-pair]
  (key->pem (.getPrivate key-pair)))

(defn key-pair->pem-public [key-pair]
  (key->pem (.getPublic key-pair)))

(defn pem->key [s]
  (let [sr (StringReader. s)
        object (.readObject (PEMParser. sr))]
    (cond
      (instance? org.bouncycastle.openssl.PEMKeyPair
                 object) (-> (doto (JcaPEMKeyConverter.)
                               (.setProvider "BC"))
                             (.getKeyPair object))
      (instance? org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
                 object) (-> (doto (JcaPEMKeyConverter.)
                               (.setProvider "BC"))
                             (.getPublicKey object)))))


;(-> (generate-key-pair) key-pair->pem-private)


;;; signing ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- ->byte-array [x]
  (if (instance? (Class/forName "[B") x)
    x
    (.getBytes x)))

(defn- signature-instance []
  (Signature/getInstance "SHA256withECDSA", "BC"))

(defn signature [k message]
  "Returns the Base64 encoded signature of the message msg (ByteArray, String)
  for k (KeyPair, PrivateKey)."
  (-> (doto (signature-instance)
        (.initSign (private-key k))
        (.update (->byte-array message)))
      .sign encoder/encode))


(defn signature-valid? [k message signature]
  (.verify
    (doto (signature-instance)
      (.initVerify (public-key k))
      (.update (->byte-array message)))
    (encoder/decode signature)))


;### Debug ####################################################################
;(debug/debug-ns *ns*)
;(debug/debug-ns 'clojure.tools.cli)
;(debug/debug-ns 'cider-ci.utils.config)
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
