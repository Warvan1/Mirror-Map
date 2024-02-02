package com.github.warvan1.mirrormap.maxmind;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;

import org.codehaus.plexus.archiver.tar.TarGZipUnArchiver;
import org.codehaus.plexus.logging.console.ConsoleLoggerManager;
import org.codehaus.plexus.util.FileUtils;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import com.maxmind.geoip2.model.CityResponse;

import io.github.cdimascio.dotenv.Dotenv;

public class DatabaseHandler {
    
    private static final String DATABASE_URL = "https://download.maxmind.com/app/geoip_download?edition_id=GeoLite2-City&suffix=tar.gz&license_key=";
    private static final String CHECKSUM_URL = "https://download.maxmind.com/app/geoip_download?edition_id=GeoLite2-City&suffix=tar.gz.sha256&license_key=";
    private static final String DATABASE_FILENAME = "GeoLite2-City.tar.gz";
    private static final String CHECKSUM_FILENAME = "GeoLite2-City.tar.gz.sha256";
    private static final String DATABASE_DESTINATION_DIR = "GeoLite2City";
    private static final int BUFFER_SIZE = 4096;

    private Dotenv dotenv = Dotenv.load();
    private String maxmindLisense = dotenv.get("MAXMIND_LISENSE");

    private DatabaseReader reader = null;

    public void ConfigureHandler() throws IOException {
        File geoLite2Directory = new File(DATABASE_DESTINATION_DIR + "/" + new File(DATABASE_DESTINATION_DIR).list()[0] + "/GeoLite2-City.mmdb");
        this.reader = new DatabaseReader.Builder(geoLite2Directory).build();
    }

    public double[] getLatLong(String ipAddress) throws IOException, GeoIp2Exception{
        if(reader == null){
            throw new GeoIp2Exception("Database Reader Not Configured.");
        }
        InetAddress ip = InetAddress.getByName(ipAddress);
        CityResponse response = reader.city(ip);
 
        double[] longlat = new double[2];
        longlat[0] = response.getLocation().getLatitude();
        longlat[1] = response.getLocation().getLongitude();
        return longlat;
    }

    public void downloadDatabase() throws IOException, GeneralSecurityException{
        downloadFile(DATABASE_URL + maxmindLisense, DATABASE_FILENAME);
        downloadFile(CHECKSUM_URL + maxmindLisense, CHECKSUM_FILENAME);

        String checksum = Files.readString(Path.of(CHECKSUM_FILENAME)).split(" ", 2)[0];

        byte[] database = Files.readAllBytes(Path.of(DATABASE_FILENAME));
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(database);
        String calculated_checksum = new BigInteger(1, hash).toString(16);

        System.out.println(checksum);
        System.out.println(calculated_checksum);

        if(!checksum.equals(calculated_checksum)){
            throw new GeneralSecurityException("checksum failed.");
        }

        final TarGZipUnArchiver ua = new TarGZipUnArchiver();

        //TarGZip loging setup
        ConsoleLoggerManager manager = new ConsoleLoggerManager();
        manager.initialize();
        ua.enableLogging(manager.getLoggerForComponent(""));
        //end of logging setup

        File destDir = new File(DATABASE_DESTINATION_DIR);
        if(!destDir.exists()){
            destDir.mkdir();
        }
        else{
            FileUtils.cleanDirectory(destDir);
        }
        ua.setSourceFile(new File(DATABASE_FILENAME));
        ua.setDestDirectory(destDir);
        ua.extract();
    }

    private void downloadFile(String urls, String fileName) throws IOException {
        URL url = new URL(urls);
        HttpURLConnection httpConn = (HttpURLConnection) url.openConnection();
        int responseCode = httpConn.getResponseCode();

        if(responseCode == HttpURLConnection.HTTP_OK){
            InputStream inputStream = httpConn.getInputStream();

            FileOutputStream outputStream = new FileOutputStream(fileName);

            int bytesRead = -1;
            byte[] buffer = new byte[BUFFER_SIZE];
            while ((bytesRead = inputStream.read(buffer)) != -1){
                outputStream.write(buffer, 0, bytesRead);
            }

            outputStream.close();
            inputStream.close();
        }
    }
}
