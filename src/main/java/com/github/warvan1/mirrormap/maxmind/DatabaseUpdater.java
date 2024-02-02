package com.github.warvan1.mirrormap.maxmind;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.Thread;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;

import org.codehaus.plexus.archiver.tar.TarGZipUnArchiver;
import org.codehaus.plexus.logging.console.ConsoleLoggerManager;
import org.codehaus.plexus.util.FileUtils;

import io.github.cdimascio.dotenv.Dotenv;

public class DatabaseUpdater implements Runnable{

    private static final String DATABASE_URL = "https://download.maxmind.com/app/geoip_download?edition_id=GeoLite2-City&suffix=tar.gz&license_key=";
    private static final String CHECKSUM_URL = "https://download.maxmind.com/app/geoip_download?edition_id=GeoLite2-City&suffix=tar.gz.sha256&license_key=";
    private static final String DATABASE_FILENAME = "GeoLite2-City.tar.gz";
    private static final String CHECKSUM_FILENAME = "GeoLite2-City.tar.gz.sha256";
    private static final String DATABASE_DESTINATION_DIR = "GeoLite2City";
    private static final int BUFFER_SIZE = 4096;

    //read maxmind lisense key from .env file
    private Dotenv dotenv = Dotenv.load();
    private String maxmindLisense = dotenv.get("MAXMIND_LISENSE");

    //used to update the database every 24 hours in a thread
    public void run(){
        while(true){
            try{
                //get a pointer to the maxmind handler object
                DatabaseHandler maxmind = DatabaseHandler.getInstance();
                //download the database
                downloadDatabase();
                //configure the database handler for the database file
                maxmind.ConfigureHandler();
    
                //sleep for 1 day
                Thread.sleep(86400000);
            }
            catch(IOException | GeneralSecurityException | InterruptedException e){
                System.err.println(e);
            }
        }
    }

    //downloads the maxmind GeoLite2-City database
    private void downloadDatabase() throws IOException, GeneralSecurityException{
        //download the Database tar.gz file and the checksum file
        downloadFile(DATABASE_URL + maxmindLisense, DATABASE_FILENAME);
        downloadFile(CHECKSUM_URL + maxmindLisense, CHECKSUM_FILENAME);

        //retrieve the checksum from the checksum file
        String checksum = Files.readString(Path.of(CHECKSUM_FILENAME)).split(" ", 2)[0];

        //compute the checksum from the database tar.gz file
        byte[] database = Files.readAllBytes(Path.of(DATABASE_FILENAME));
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(database);
        String calculated_checksum = new BigInteger(1, hash).toString(16);

        //check to make sure that both the downloaded checksum and the calculated checksum are the same
        if(!checksum.equals(calculated_checksum)){
            throw new GeneralSecurityException("checksum failed.");
        }

        //object used to unzip the database tar.gz file
        final TarGZipUnArchiver ua = new TarGZipUnArchiver();

        //set up loging for TarGZip as its required for it to function
        ConsoleLoggerManager manager = new ConsoleLoggerManager();
        manager.initialize();
        ua.enableLogging(manager.getLoggerForComponent(""));

        //create a new File object for the location where we want to extract to
        File destDir = new File(DATABASE_DESTINATION_DIR);
        //if it doesnt exist create the folder 
        //else empty the folder of its contents
        if(!destDir.exists()){
            destDir.mkdir();
        }
        else{
            FileUtils.cleanDirectory(destDir);
        }
        //extract the file
        ua.setSourceFile(new File(DATABASE_FILENAME));
        ua.setDestDirectory(destDir);
        ua.extract();
    }

    //downloads a file from a given url over http
    private void downloadFile(String url, String fileName) throws IOException {
        //open a http connection to the given url 
        HttpURLConnection httpConn = (HttpURLConnection) new URL(url).openConnection();
        int responseCode = httpConn.getResponseCode();

        //check to make sure that connection is established successfully
        if(responseCode == HttpURLConnection.HTTP_OK){
            //initialize input stream from the http connection
            InputStream inputStream = httpConn.getInputStream();

            //initialize output stream to the file
            FileOutputStream outputStream = new FileOutputStream(fileName);

            //read the bytes of the file from the http input stream and output them to the file output stream
            int bytesRead = -1;
            byte[] buffer = new byte[BUFFER_SIZE];
            while ((bytesRead = inputStream.read(buffer)) != -1){
                outputStream.write(buffer, 0, bytesRead);
            }

            outputStream.close();
            inputStream.close();
        }
        else{
            throw new IOException("Failed to connect to " + url + " over http\nResponse Code: " + responseCode);
        }
    }
}
