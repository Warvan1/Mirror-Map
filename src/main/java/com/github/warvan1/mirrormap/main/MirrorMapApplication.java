package com.github.warvan1.mirrormap.main;

import java.io.IOException;
import java.security.GeneralSecurityException;

import com.github.warvan1.mirrormap.maxmind.DatabaseHandler;

import com.maxmind.geoip2.exception.GeoIp2Exception;

public class MirrorMapApplication {

    public static void main(String[] args){
        try{
            DatabaseHandler maxmind = new DatabaseHandler();

            maxmind.downloadDatabase();
            maxmind.ConfigureHandler();

            double [] latlong = maxmind.getLatLong("128.153.197.71");
            System.out.println(latlong[0] + " " + latlong[1]);
        }
        catch(IOException | GeneralSecurityException | GeoIp2Exception e){
            System.err.println(e);
            return;
        }
        
    }
}