package SpeechAPIDemo;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.client.DefaultSSLWebSocketClientFactory;
import org.java_websocket.handshake.ServerHandshake;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import javax.net.ssl.SSLContext;
import java.security.*;

import java.net.URI;

/**
 * Created by laurensw on 13-12-16.
 */
class WorkerCountClient extends WebSocketClient {

    private WorkerCountInterface handler;

    public WorkerCountClient(URI serverURI, WorkerCountInterface handler) {
        super(serverURI);
        this.handler = handler;

        SSLContext sslContext = null;
        try {
            sslContext = SSLContext.getInstance( "TLS" );
            sslContext.init( null, null, null ); // will use java's default key and trust store which is sufficient unless you deal with self-signed certificates
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        }

        this.setWebSocketFactory( new DefaultSSLWebSocketClientFactory( sslContext ) );
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
    }

    @Override
    public void onError(Exception arg0) {
        System.err.println("****** Exception: "+arg0);
        this.handler.notifyWorkerCount( -1 );
    }

    @Override
    public void onMessage(String msg) {
        Object obj = JSONValue.parse(msg);

        if ( obj != null ) {
            JSONObject jsonObj = (JSONObject) obj;
            if (jsonObj.containsKey("description")) {
                Object lo =jsonObj.get("description");
                String description = lo.toString();
                this.handler.notifyDescription( description );
            }
            if (jsonObj.containsKey("num_workers_available")) {
                Object lo = jsonObj.get("num_workers_available");
                int n_workers = ((Long)lo).intValue();
                this.handler.notifyWorkerCount( n_workers );
            }
            if (jsonObj.containsKey("num_requests_processed")) {
                Object lo = jsonObj.get("num_requests_processed");
                int n_requests = ((Long)lo).intValue();
                this.handler.notifyRequests( n_requests );
            }
            System.err.println("****** Message = "+msg);
        }
    }

    @Override
    public void onOpen(ServerHandshake arg0) {
    }

}
