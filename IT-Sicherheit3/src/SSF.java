import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.EncodedKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

/**
 * 
 * Ein Objekt der Klasse SSF verschl�sselt und signiert mit Hilfe von RSA und AES eine Datei
 * 
 * 
 * @author Timo Feddersen und Sergej Kimel
 * @version 1.0
 */

public class SSF {

	String rsaPrv, rsaPub, dokument, ssfFile;  //Eingabeparameter
	PublicKey pubKey = null;   // �ffentlicher RSA Schl�ssel
	PrivateKey prvKey = null;  // Privater RSA Schl�ssel
	byte[] aeskey = null;      // AES Schl�ssel
	byte[] encryptedAesKey;    // AES Schl�ssel, mit Oeffentlichen RSA Schl�ssel verschl�sselt
	byte[] signature = null;   // AES Signatur
	byte[] encryptedDokument = null; // Das mit AES verschl�sselte Dokument

	public SSF(String rsaPrv, String rsaPub, String dokument, String ssfFile) {
		this.rsaPrv = rsaPrv;
		this.rsaPub = rsaPub;
		this.dokument = dokument;
		this.ssfFile = ssfFile;
	}

	public static void main(String[] args) {

		SSF ssf = new SSF(args[0], args[1], args[2], args[3]);

		// Public key einlesen
		ssf.readRSAPublic();

		// Private Key einlesen
		ssf.readRSAPrivate();

		// AES Schl�ssel erzeugen
		ssf.generateAESKey();

		// Signatur f�r den AES Schl�ssel erstellen (mit dem �ffentlichen RSA Schl�ssel)
		ssf.signAESKey();

		// AES Schl�ssel mit dem Privaten RSA Schl�ssel verschl�sseln
		ssf.encryptAESKey();

		// Das Dokument mit dem AES Schl�ssel verschl�sseln
		ssf.encryptDokument();
		
		//Daten in eine ssf Datei schreiben
		ssf.writeToFile();

	}

	/**
	 * Diese Methode liest aus einer der Pub Datei, den Public RSA Schl�ssel aus, Und
	 * generiert daraus ein neues PublicKey Objekt
	 * 
	 */

	public void readRSAPublic() {

		byte[] inhaber = null;
		byte[] pubKeyEnc = null;

		try {
			// die Datei wird ge�ffnet und die Daten gelesen
			DataInputStream is = new DataInputStream(
					new FileInputStream(rsaPub));

			// die L�nge des Inhabers
			int len = is.readInt();
			inhaber = new byte[len];
			// der Inhaber
			is.read(inhaber);
			
			// die L�nge des schl�ssels
			len = is.readInt();
			pubKeyEnc = new byte[len];
			// der schl�ssel
			is.read(pubKeyEnc);

			is.close();

		} catch (IOException e) {
			Error("readRSAPublic(): Datei-Fehler beim Lesen der Nachricht!", e);
		}

		KeyFactory keyFac;

		try {

			// nun wird aus der Kodierung wieder ein �ffentlicher Schl�ssel erzeugt
			keyFac = KeyFactory.getInstance("RSA");
			
			// aus dem Byte-Array k�nnen wir eine X.509-Schl�sselspezifikation erzeugen
			X509EncodedKeySpec x509KeySpec = new X509EncodedKeySpec(pubKeyEnc);

			// und in einen abgeschlossene, providerabh�ngigen Schl�ssel konvertieren
			pubKey = keyFac.generatePublic(x509KeySpec);

		} catch (NoSuchAlgorithmException e) {
			Error("readRSAPublic(): Es existiert keine Implementierung f�r RSA.", e);
		} catch (InvalidKeySpecException e) {
			Error("readRSAPublic() :Fehler beim Konvertieren des �ffentlichen Schl�ssels.", e);
			e.printStackTrace();
		}

	}

	/**
	 * Diese Methode liest aus einer der prv Datei, den Private RSA Schl�ssel aus, Und
	 * generiert daraus ein neues PrivateKey Objekt
	 * 
	 */

	public void readRSAPrivate() {

		byte[] inhaber = null;
		byte[] prvKeyEnc = null;

		try {
			// die Datei wird ge�ffnet und die Daten gelesen
			DataInputStream is = new DataInputStream(
					new FileInputStream(rsaPrv));

			// die L�nge des Inhabers
			int len = is.readInt();
			inhaber = new byte[len];
			// der Inhaber
			is.read(inhaber);

			// die L�nge des schl�ssels
			len = is.readInt();
			prvKeyEnc = new byte[len];
			// der schl�ssel
			is.read(prvKeyEnc);

			is.close();

		} catch (IOException e) {
			Error("readRSAPrivate(): Datei-Fehler beim Lesen der Nachricht!", e);
		}

		KeyFactory keyFac;

		try {

			// nun wird aus der Kodierung wieder ein Privater Schl�ssel erzeugt
			keyFac = KeyFactory.getInstance("RSA");
			
			// aus dem Byte-Array k�nnen wir eine PKCS8-Schl�sselspezifikationerzeugen
			EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(prvKeyEnc); // warum PKCS8???
			
			// und in einen abgeschlossene, providerabh�ngigen Schl�ssel konvertieren
			prvKey = keyFac.generatePrivate(privateKeySpec);

		} catch (NoSuchAlgorithmException e) {
			Error("readRSAPrivate(): Es existiert keine Implementierung f�r RSA.", e);
		} catch (InvalidKeySpecException e) {
			Error("readRSAPrivate(): Fehler beim Konvertieren des Privaten Schl�ssels.", e);
			e.printStackTrace();
		}

	}

	/**
	 * Diese Methode erzeugt einen 128bit AES Schl�ssel
	 */

	public void generateAESKey() {

		try {

			KeyGenerator kg;
			// AES-Schl�ssel generieren
			kg = KeyGenerator.getInstance("AES");
			kg.init(128); // Schl�ssell�nge 128 Bit
			aeskey = kg.generateKey().getEncoded();

		} catch (NoSuchAlgorithmException e) {
			Error("generateAESKey(): Es existiert keine Implementierung f�r AES.", e);
		}

	}

	/**
	 * Diese Methode erstellt eine Signatur des AES Schl�ssels mit dem privaten RSA Schl�ssel
	 */

	public void signAESKey() {

		Signature rsaSig = null;

		try {
			// als Erstes erzeugen wir das Signatur-Objekt
			rsaSig = Signature.getInstance("SHA1withRSA");
			// zum Signieren ben�tigen wir den geheimen Schl�ssel
			rsaSig.initSign(prvKey);
			// Daten zum Signieren liefern
			rsaSig.update(aeskey);
			// Signatur f�r die Daten erzeugen
			signature = rsaSig.sign();
		} catch (NoSuchAlgorithmException ex) {
			Error("signAESKey(): Keine Implementierung f�r SHA1withRSA!", ex);

		} catch (InvalidKeyException e) {
			Error("signAESKey(): Falscher Schl�ssel!", e);
		} catch (SignatureException e) {
			Error("signAESKey(): Fehler beim Signieren der Nachricht!", e);
		}
	}

	/**
	 * Diese Methode verschl�sselt den AES Schl�ssel mit dem Privaten RSA Schl�ssel
	 */

	public void encryptAESKey() {

		try {
			// Cipher Objekt erzeugen
			Cipher cipher = Cipher.getInstance("RSA");

			// Cipher Objekt initialisieren
			cipher.init(Cipher.ENCRYPT_MODE, pubKey);

			// AES Schl�ssel verschl�sseln
			encryptedAesKey = cipher.doFinal(aeskey);
			
		
		} catch (NoSuchAlgorithmException e) {
			Error("encryptAESKey(): Keine Implementierung f�r RSA", e);
		} catch (NoSuchPaddingException e) {
			Error("encryptAESKey(): Keine Implementierung f�r das Padding", e);
		} catch (InvalidKeyException e) {
			Error("encryptAESKey(): Falscher Schl�ssel!", e);
		} catch (IllegalBlockSizeException e) {
			Error("encryptAESKey(): Falsche Blockgr��e!", e);
		} catch (BadPaddingException e) {
			Error("", e);
		}

	}

	/**
	 * Diese Methode verschl�sselt mit dem AES Key, das Dokument
	 */

	public void encryptDokument() {

		try {

			DataInputStream is = new DataInputStream(new FileInputStream(
					dokument));

			// Cipher Objekt erzeugen
			Cipher encryptCipher = Cipher.getInstance("AES");
			SecretKeySpec specKey = new SecretKeySpec(aeskey, "AES");

			// Ciper initialisieren
			encryptCipher.init(Cipher.ENCRYPT_MODE, specKey);

			// Dokument komplett einlesen
			File file = new File(dokument); // geht das auch anders???
			int len = (int) file.length();
			byte buf[] = new byte[len];
			is.read(buf, 0, len);
			is.close();
			// Dokument verschl�sseln
			encryptedDokument = encryptCipher.doFinal(buf);


			
		} catch (NoSuchAlgorithmException e) {
			Error("encryptDokument(): Keine Implementierung f�r AES", e);
		} catch (NoSuchPaddingException e) {
			Error("encryptDokument(): Keine Implementierung f�r das Padding", e);
		} catch (InvalidKeyException e) {
			Error("encryptDokument(): Ung�ltig Formatierter Schl�ssel", e);
		} catch (FileNotFoundException e) {
			Error("encryptDokument(): Datei Fehler", e);
		} catch (IOException e) {
			Error("encryptDokument(): Datei Fehler", e);
		} catch (IllegalBlockSizeException e) {
			Error("encryptDokument(): Falsche Blockgroesse", e);
		} catch (BadPaddingException e) {
			Error("encryptDokument(): Padding Fehler", e);
		}

	}
	
	
	/**
	 * Diese Methode schreibt vollgende Daten in eine .ssf Datei:
	 * L�nge des verschl�sselten geheimen Schl�ssels (integer)
	 * Verschl�sselter geheimer Schl�ssel (Bytefolge)
	 * L�nge der Signatur des geheimen Schl�ssels (integer)
	 * Signatur des geheimen Schl�ssels (Bytefolge)
	 * Verschl�sselte Dateidaten (Bytefolge)
	 * 
	 */
	
	public void writeToFile(){
		
		
		try {
			
			//Erstellt einen neuen Output Stream
			DataOutputStream out = new DataOutputStream((new FileOutputStream(ssfFile)));
			
			//Daten in Datei Schreiben
			out.writeInt(encryptedAesKey.length);
			out.write(encryptedAesKey);
			out.writeInt(signature.length);
			out.write(signature);
            out.write(encryptedDokument);
			out.close();
			
		} catch (FileNotFoundException e) {
			Error("writeToFile(): Datei Fehler beim schreiben der Nachricht", e);
		} catch (IOException e) {
			Error("writeToFile(): Datei Fehler beim schreiben der Nachricht", e);
		}
		
		
	}

	/**
	 * Diese Methode gibt eine Fehlermeldung sowie eine Beschreibung der
	 * Ausnahme aus. Danach wird das Programm beendet.
	 * 
	 * @param msg
	 *            eine Beschreibung f�r den Fehler
	 * @param ex
	 *            die Ausnahme, die den Fehler ausgel�st hat
	 */
	private final static void Error(String msg, Exception ex) {
		System.out.println(msg);
		System.out.println(ex.getMessage());
		System.exit(0);
	}

}
