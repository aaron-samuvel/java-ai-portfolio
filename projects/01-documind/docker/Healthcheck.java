import java.net.HttpURLConnection;
import java.net.URI;

public class Healthcheck {
    public static void main(String[] args) throws Exception {
        HttpURLConnection connection = (HttpURLConnection)
                URI.create("http://127.0.0.1:8081/actuator/health").toURL().openConnection();
        connection.setConnectTimeout(2000);
        connection.setReadTimeout(2000);
        connection.setRequestMethod("GET");
        int status = connection.getResponseCode();
        connection.disconnect();
        if (status < 200 || status >= 300) {
            System.exit(1);
        }
    }
}

