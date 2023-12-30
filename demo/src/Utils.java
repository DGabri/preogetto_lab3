package demo.src;

import java.io.FileInputStream;
import java.io.IOException;

import java.util.Properties;

public class Utils {

    // function to load config variables for server
    public static Properties loadConfig(String fname) {
        try (FileInputStream fileInputStream = new FileInputStream(fname)) {
            Properties properties = new Properties();
            properties.load(fileInputStream);

            return properties;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    // function to split the credentials as username_password
    public static String[] splitCredentials(String credentials) {
        String[] parts = credentials.split("_");

        return parts;
    }
}
