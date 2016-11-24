// package SpeechAPIDemo;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.*;
import javax.swing.*;

import java.awt.*;
import java.awt.event.*;
import javax.sound.sampled.*;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import java.net.URI;
import java.util.List;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.simple.parser.JSONParser;

//
// The following code was taken from https://github.com/Kaljurand/net-speech-api
// part of Net Speech API.
// Copyright 2011, Institute of Cybernetics at Tallinn University of Technology
//

interface WorkerCountInterface {
    void notifyWorkerCount(int count);
    void notifyRequests(int count);
    void notifyDescription(String description);
}

interface DuplexRecognitionSession {
    void connect() throws IOException;
    void sendChunk(byte[] var1, boolean var2) throws IOException;
    void addRecognitionEventListener(RecognitionEventListener var1);
}

interface RecognitionEventListener {
    void onRecognitionEvent(RecognitionEvent var1);
    void onClose();
}

class WorkerCountClient extends WebSocketClient {

    private WorkerCountInterface handler;

    public WorkerCountClient(URI serverURI, WorkerCountInterface handler) {
        super(serverURI);
        this.handler = handler;
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
    }

    @Override
    public void onError(Exception arg0) {
        System.err.println("****** Exception: "+arg0);
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
        }
    }

    @Override
    public void onOpen(ServerHandshake arg0) {
    }

}

class RecognitionEvent {
    public static final int STATUS_SUCCESS = 0;
    public static final int STATUS_NO_SPEECH = 1;
    public static final int STATUS_ABORTED = 2;
    public static final int STATUS_AUDIO_CAPTURE = 3;
    public static final int STATUS_NETWORK = 4;
    public static final int STATUS_NOT_ALLOWED = 5;
    public static final int STATUS_SERVICE_NOT_ALLOWED = 6;
    public static final int STATUS_BAD_GRAMMAR = 7;
    public static final int STATUS_LANGUAGE_NOT_SUPPORTED = 8;
    private int status;
    private RecognitionEvent.Result result;

    public RecognitionEvent(int status, RecognitionEvent.Result result) {
        this.status = status;
        this.result = result;
    }

    public int getStatus() {
        return this.status;
    }

    public RecognitionEvent.Result getResult() {
        return this.result;
    }

    public int hashCode() {
        boolean prime = true;
        byte result = 1;
        int result1 = 31 * result + (this.result == null?0:this.result.hashCode());
        result1 = 31 * result1 + this.status;
        return result1;
    }

    public boolean equals(Object obj) {
        if(this == obj) {
            return true;
        } else if(obj == null) {
            return false;
        } else if(this.getClass() != obj.getClass()) {
            return false;
        } else {
            RecognitionEvent other = (RecognitionEvent)obj;
            if(this.result == null) {
                if(other.result != null) {
                    return false;
                }
            } else if(!this.result.equals(other.result)) {
                return false;
            }

            return this.status == other.status;
        }
    }

    public String toString() {
        return "RecognitionEvent [result=" + this.result + ", status=" + this.status + "]";
    }

    public static class Hypothesis {
        private String transcript;
        private float confidence;

        public Hypothesis(String transcript, float confidence) {
            this.transcript = transcript;
            this.confidence = confidence;
        }

        public String getTranscript() {
            return this.transcript;
        }

        public float getConfidence() {
            return this.confidence;
        }

        public int hashCode() {
            boolean prime = true;
            byte result = 1;
            int result1 = 31 * result + Float.floatToIntBits(this.confidence);
            result1 = 31 * result1 + (this.transcript == null?0:this.transcript.hashCode());
            return result1;
        }

        public boolean equals(Object obj) {
            if(this == obj) {
                return true;
            } else if(obj == null) {
                return false;
            } else if(this.getClass() != obj.getClass()) {
                return false;
            } else {
                RecognitionEvent.Hypothesis other = (RecognitionEvent.Hypothesis)obj;
                if(Float.floatToIntBits(this.confidence) != Float.floatToIntBits(other.confidence)) {
                    return false;
                } else {
                    if(this.transcript == null) {
                        if(other.transcript != null) {
                            return false;
                        }
                    } else if(!this.transcript.equals(other.transcript)) {
                        return false;
                    }

                    return true;
                }
            }
        }

        public String toString() {
            return "Hypothesis [confidence=" + this.confidence + ", transcript=" + this.transcript + "]";
        }
    }

    public static class Result {
        private boolean isFinal;
        private List<RecognitionEvent.Hypothesis> hypotheses;

        public Result(boolean isFinal, List<RecognitionEvent.Hypothesis> hypotheses) {
            this.isFinal = isFinal;
            this.hypotheses = hypotheses;
        }

        public boolean isFinal() {
            return this.isFinal;
        }

        public List<RecognitionEvent.Hypothesis> getHypotheses() {
            return this.hypotheses;
        }

        public int hashCode() {
            boolean prime = true;
            byte result = 1;
            int result1 = 31 * result + (this.hypotheses == null?0:this.hypotheses.hashCode());
            result1 = 31 * result1 + (this.isFinal?1231:1237);
            return result1;
        }

        public boolean equals(Object obj) {
            if(this == obj) {
                return true;
            } else if(obj == null) {
                return false;
            } else if(this.getClass() != obj.getClass()) {
                return false;
            } else {
                RecognitionEvent.Result other = (RecognitionEvent.Result)obj;
                if(this.hypotheses == null) {
                    if(other.hypotheses != null) {
                        return false;
                    }
                } else if(!this.hypotheses.equals(other.hypotheses)) {
                    return false;
                }

                return this.isFinal == other.isFinal;
            }
        }

        public String toString() {
            return "Result [hypotheses=" + this.hypotheses + ", final=" + this.isFinal + "]";
        }
    }
}

class WsDuplexRecognitionSession implements DuplexRecognitionSession {
    private String serverUrl;
    private WsDuplexRecognitionSession.MyWsClient wsClient;
    private List<RecognitionEventListener> recognitionEventListeners = new LinkedList();
    private Map<String, String> parameters;

    public WsDuplexRecognitionSession(String serverUrl) throws IOException, URISyntaxException {
        this.serverUrl = serverUrl;
        this.parameters = new HashMap();
        this.parameters.put("content-type", "audio/x-raw, layout=(string)interleaved, rate=(int)16000, format=(string)S16LE, channels=(int)1");
    }

    public void connect() throws IOException {
        String parameterString = "";
        Map.Entry serverURI;
        if(this.parameters != null) {
            for(Iterator e = this.parameters.entrySet().iterator(); e.hasNext(); parameterString = parameterString + URLEncoder.encode((String)serverURI.getKey() + "=" + (String)serverURI.getValue(), "UTF-8")) {
                serverURI = (Map.Entry)e.next();
                if(parameterString.isEmpty()) {
                    parameterString = "?";
                } else {
                    parameterString = parameterString + "&";
                }
            }
        }

        try {
            URI serverURI1 = new URI(this.serverUrl + parameterString);
            this.wsClient = new WsDuplexRecognitionSession.MyWsClient(serverURI1);
            this.wsClient.connectBlocking();
        } catch (URISyntaxException var4) {
            throw new IOException(var4);
        } catch (InterruptedException var5) {
            throw new IOException(var5);
        }
    }

    public void addRecognitionEventListener(RecognitionEventListener recognitionEventListener) {
        this.recognitionEventListeners.add(recognitionEventListener);
    }

    public void sendChunk(byte[] bytes, boolean isLast) throws IOException {
        this.wsClient.send(bytes);
        if(isLast) {
            this.wsClient.send("EOS");
        }

    }

    public static RecognitionEvent parseRecognitionEventJson(String json) {
        Object obj = JSONValue.parse(json);
        if(obj == null) {
            return null;
        } else {
            JSONObject jsonObj = (JSONObject)obj;
            long status = ((Long)jsonObj.get("status")).longValue();
            RecognitionEvent.Result result = null;
            if(jsonObj.containsKey("result")) {
                JSONObject jo1 = (JSONObject)jsonObj.get("result");
                boolean isFinal = ((Boolean)jo1.get("final")).booleanValue();
                ArrayList hyps = new ArrayList();

                String transcript;
                double confidence;
                for(Iterator var10 = ((JSONArray)jo1.get("hypotheses")).iterator(); var10.hasNext(); hyps.add(new RecognitionEvent.Hypothesis(transcript, (float)confidence))) {
                    Object o1 = var10.next();
                    JSONObject jo2 = (JSONObject)o1;
                    transcript = (String)jo2.get("transcript");
                    confidence = 1.0D;
                    if(jo2.containsKey("confidence")) {
                        confidence = ((Double)jo2.get("confidence")).doubleValue();
                    }
                }

                result = new RecognitionEvent.Result(isFinal, hyps);
            }

            return new RecognitionEvent((int)status, result);
        }
    }

    public void setContentType(String contentType) {
        this.parameters.put("content-type", contentType);
    }

    public void setUserId(String userId) {
        this.parameters.put("user-id", userId);
    }

    public void setContentId(String contentId) {
        this.parameters.put("content-id", contentId);
    }

    class MyWsClient extends WebSocketClient {
        private JSONParser parser = new JSONParser();

        public MyWsClient(URI serverURI) {
            super(serverURI);
        }

        public void onClose(int code, String reason, boolean remote) {
            Iterator var5 = WsDuplexRecognitionSession.this.recognitionEventListeners.iterator();

            while(var5.hasNext()) {
                RecognitionEventListener listener = (RecognitionEventListener)var5.next();
                listener.onClose();
            }

        }

        public void onError(Exception arg0) {
        }

        public void onMessage(String msg) {
            RecognitionEvent event = WsDuplexRecognitionSession.parseRecognitionEventJson(msg);
            if(event != null) {
                Iterator var4 = WsDuplexRecognitionSession.this.recognitionEventListeners.iterator();

                while(var4.hasNext()) {
                    RecognitionEventListener listener = (RecognitionEventListener)var4.next();
                    listener.onRecognitionEvent(event);
                }
            }

        }

        public void onOpen(ServerHandshake arg0) {
        }
    }
}

//
// The preceding code was taken from https://github.com/Kaljurand/net-speech-api
//

public class SpeechAPIDemo extends JFrame {

    private static final long serialVersionUID = 1L;

    private static JTextArea textArea;
    private static JTextArea resultArea;

    boolean stopCapture = false;
    static boolean bufferFilled = false;
    static boolean workersAvailable = false;

    final static JButton filechooseBtn = new JButton("Select File");
    final static JButton captureBtn = new JButton("Capture");
    final static JButton stopBtn = new JButton("Stop");
    final static JButton playBtn = new JButton("Recognize");
    final static JCheckBox liveBox = new JCheckBox("Live Recognition");

    private static JLabel statusLabel = new JLabel("Available Slots: ");
    private static JLabel langLabel = new JLabel("Language / Modeltype: ");

    static ByteArrayOutputStream byteArrayOutputStream;
    AudioFormat audioFormat;
    TargetDataLine targetDataLine;
    static AudioInputStream audioInputStream;

    private static String DEFAULT_WS_URL;
    private static String DEFAULT_WS_STATUS_URL;

    static class RecognitionEventAccumulator implements RecognitionEventListener, WorkerCountInterface {

        private List<RecognitionEvent> events = new ArrayList<RecognitionEvent>();
        private boolean closed = false;

        public void notifyWorkerCount(int count) {
            if (count>0) {
                workersAvailable = true;
                filechooseBtn.setEnabled(true);
                captureBtn.setEnabled(true);
                if(bufferFilled) {
                    playBtn.setEnabled(true);
                }
            } else {
                workersAvailable = false;
                filechooseBtn.setEnabled(false);
                playBtn.setEnabled(false);
                if (liveBox.isSelected()) {
                    captureBtn.setEnabled(false);
                }
            }
            statusLabel.setText("Available slots: "+count);
            System.err.println("****** N_WORKERS = "+count);
        }

        public void notifyRequests(int count) {
            System.err.println("****** N_REQUESTS = "+count);
        }

        public void notifyDescription(String description) {
            Object obj = JSONValue.parse(description);
            if ( obj != null ) {
                String lang="";
                String modtype="";
                JSONObject jsonObj = (JSONObject) obj;
                if (jsonObj.containsKey("language")) {
                    lang=(String) jsonObj.get("language");
                }
                if (jsonObj.containsKey("modeltype")) {
                    modtype=(String) jsonObj.get("modeltype");
                }
                langLabel.setText("Language / Modeltype: "+lang+" / "+modtype);
                System.err.println("****** DESCRIPTION = "+jsonObj.get("identifier"));
                // System.err.println("****** DESCRIPTION = "+jsonObj);
            }
        }

        public void onClose() {
            closed = true;
            this.notifyAll();
        }

        public void onRecognitionEvent(RecognitionEvent event) {
            events.add(event);
            System.err.println("Got event:" + event);
            textArea.setText(event.getResult().getHypotheses().get(0).getTranscript());
            textArea.update(textArea.getGraphics());
            if (event.getResult().isFinal()) {
                resultArea.append(event.getResult().getHypotheses().get(0).getTranscript() + " (" + String.format("%.3f", event.getResult().getHypotheses().get(0).getConfidence()) + ")\n");
                resultArea.update(resultArea.getGraphics());
            }
        }

        public List<RecognitionEvent> getEvents() {
            return events;
        }

        public boolean isClosed() {
            return closed;
        }
    }

    private static String testRecognition(File SpeechFile) throws MalformedURLException, IOException, URISyntaxException, InterruptedException {
        RecognitionEventAccumulator eventAccumulator = new RecognitionEventAccumulator();
        WsDuplexRecognitionSession session = new WsDuplexRecognitionSession(DEFAULT_WS_URL);
        session.addRecognitionEventListener(eventAccumulator);

        String extension=SpeechFile.getName().substring(SpeechFile.getName().lastIndexOf(".") + 1);
        if (extension.equals("flac")) {
            session.setContentType("audio/x-flac, layout=(string)interleaved, rate=(int)16000, format=(string)S16LE, channels=(int)1");
        }

        session.setUserId("laurensw");
        session.setContentId("HelloWorld");

        session.connect();

        sendFile(session, SpeechFile, 16000*2);
        while (!eventAccumulator.isClosed()) {
            synchronized (eventAccumulator) {
                eventAccumulator.wait(1000);
            }
        }

        RecognitionEvent lastEvent = eventAccumulator.getEvents().get(eventAccumulator.getEvents().size() - 2);
        String result="";
        result=lastEvent.getResult().getHypotheses().get(0).getTranscript();
        return result;
    }

    private static void sendFile(DuplexRecognitionSession session, File file, int bytesPerSecond) throws IOException, InterruptedException {
        InputStream in = new FileInputStream(file);
        int chunksPerSecond = 4;

        byte buf[] = new byte[bytesPerSecond / chunksPerSecond];

        while (true) {
            long millisWithinChunkSecond = System.currentTimeMillis() % (1000 / chunksPerSecond);
            int size = in.read(buf);
            System.err.println("File size:" + size);
            if (size < 0) {
                byte buf2[] = new byte[0];
                session.sendChunk(buf2, true);
                break;
            }

            if (size == bytesPerSecond / chunksPerSecond) {
                session.sendChunk(buf, false);
            } else {
                byte buf2[] = Arrays.copyOf(buf, size);
                session.sendChunk(buf2, true);
                break;
            }
            Thread.sleep(1000/chunksPerSecond - millisWithinChunkSecond);
        }

        in.close();
    }

    private static void sendStream(DuplexRecognitionSession session, ByteArrayOutputStream bAOS) throws IOException, InterruptedException {
        int chunksPerSecond = 4;

        byte audioData[] = bAOS.toByteArray();
        InputStream byteArrayInputStream = new ByteArrayInputStream(audioData);
        AudioFormat audioFormat = getAudioFormat();
        int bytesPerSecond = (int) (audioFormat.getSampleRate() * 2);

        byte buf[] = new byte[bytesPerSecond / chunksPerSecond];

        while (true) {
            long millisWithinChunkSecond = System.currentTimeMillis() % (1000 / chunksPerSecond);

            int size = byteArrayInputStream.read(buf, 0, buf.length);

            System.err.println("Chunk size:" + size);
            if (size < 0) {
                byte buf2[] = new byte[0];
                session.sendChunk(buf2, true);
                break;
            }

            if (size == bytesPerSecond / chunksPerSecond) {
                session.sendChunk(buf, false);
            } else {
                byte buf2[] = Arrays.copyOf(buf, size);
                session.sendChunk(buf2, true);
                break;
            }
            Thread.sleep(1000/chunksPerSecond - millisWithinChunkSecond);
        }
    }

    public static void main(String[] args) {
        if ((args.length==2) && (args[1].matches("[0-9]+"))) {
            DEFAULT_WS_URL = "ws://"+args[0]+":"+args[1]+"/client/ws/speech";
            DEFAULT_WS_STATUS_URL = "ws://"+args[0]+":"+args[1]+"/client/ws/status";
            new SpeechAPIDemo();
        } else {
            System.out.println("Please specify server and port to use");
        }
    }

    private static void RecognizeAudio() throws MalformedURLException, IOException, URISyntaxException, InterruptedException {
        RecognitionEventAccumulator eventAccumulator = new RecognitionEventAccumulator();
        WsDuplexRecognitionSession session = new WsDuplexRecognitionSession(DEFAULT_WS_URL);
        session.addRecognitionEventListener(eventAccumulator);
        session.setUserId("laurensw");
        session.setContentId("SpeechAPIDemo");

        session.connect();

        sendStream(session, byteArrayOutputStream);
    }

    public SpeechAPIDemo() {
        float scale = 1;
        int screenwidth = (int)Toolkit.getDefaultToolkit().getScreenSize().getWidth();
        // This is an ugly hack, but the dpi value on hidpi screens is wrong :(
        if (screenwidth > 3000) {
            scale=2;
        }

        try {
            RecognitionEventAccumulator statusEventAccumulator = new RecognitionEventAccumulator();
            URI statusUri = new URI(DEFAULT_WS_STATUS_URL);
            WorkerCountClient status_session = new WorkerCountClient(statusUri,statusEventAccumulator);
            status_session.connect();
        } catch (Exception e3){
            System.err.println("Caught Exception: " + e3.getMessage());
        }

        int fontsize=(int)(16*scale);
        filechooseBtn.setFont(new Font("Arial", Font.PLAIN, fontsize));
        captureBtn.setFont(new Font("Arial", Font.PLAIN, fontsize));
        stopBtn.setFont(new Font("Arial", Font.PLAIN, fontsize));
        playBtn.setFont(new Font("Arial", Font.PLAIN, fontsize));
        liveBox.setFont(new Font("Arial", Font.PLAIN, fontsize));

        statusLabel.setFont(new Font("Arial", Font.PLAIN, fontsize));
        langLabel.setFont(new Font("Arial", Font.PLAIN, fontsize));

        filechooseBtn.setEnabled(true);
        captureBtn.setEnabled(true);
        stopBtn.setEnabled(false);
        playBtn.setEnabled(false);

        filechooseBtn.addActionListener(
                new ActionListener(){
                    public void actionPerformed(ActionEvent e){
                        JFileChooser fc = new JFileChooser();
                        int returnVal = fc.showOpenDialog(getParent());
                        if(returnVal == JFileChooser.APPROVE_OPTION) {
                            try {
                                testRecognition(fc.getSelectedFile().getAbsoluteFile());
                            } catch (IOException|URISyntaxException|InterruptedException e2 ) {
                                System.err.println("Caught Exception: " + e2.getMessage());
                            }
                        }
                    }
                }
        );

        //Register anonymous listeners
        captureBtn.addActionListener(
                new ActionListener(){
                    public void actionPerformed(ActionEvent e){
                        captureBtn.setEnabled(false);
                        stopBtn.setEnabled(true);
                        playBtn.setEnabled(false);
                        //Capture input data from the microphone until the Stop button is clicked.
                        WsDuplexRecognitionSession session = null;
                        if (liveBox.isSelected()) {
                            try {
                                RecognitionEventAccumulator eventAccumulator = new RecognitionEventAccumulator();
                                session = new WsDuplexRecognitionSession(DEFAULT_WS_URL);
                                session.addRecognitionEventListener(eventAccumulator);
                                session.setUserId("laurensw");
                                session.setContentId("SpeechAPIDemo");
                                session.connect();
                            } catch (Exception e2){
                                System.err.println("Caught Exception: " + e2.getMessage());
                            }
                        }
                        captureAudio(session);
                    }
                }
        );

        stopBtn.addActionListener(
                new ActionListener(){
                    public void actionPerformed(ActionEvent e){
                        captureBtn.setEnabled(true);
                        stopBtn.setEnabled(false);
                        playBtn.setEnabled(true);
                        bufferFilled = true;
                        //Terminate the capturing of input data from the microphone.
                        stopCapture = true;
                        targetDataLine.close();
                    }
                }
        );

        playBtn.addActionListener(
                new ActionListener(){
                    public void actionPerformed(ActionEvent e){
                        // Perform recognition on captures audio
                        try {
                            RecognizeAudio();
                        } catch (IOException|URISyntaxException|InterruptedException e2 ) {
                            System.err.println("Caught Exception: " + e2.getMessage());
                        }
                    }
                }
        );

        textArea = new JTextArea(3,30);
        resultArea = new JTextArea(10,30);
        JScrollPane scrollPane = new JScrollPane(resultArea);
        textArea.setEditable(false);
        textArea.setLineWrap(true);
        textArea.setFont(new Font("Arial", Font.PLAIN, fontsize));
        resultArea.setEditable(false);
        resultArea.setLineWrap(true);
        resultArea.setFont(new Font("Arial", Font.PLAIN, fontsize));

        GroupLayout layout=new GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setAutoCreateGaps(true);
        layout.setAutoCreateContainerGaps(true);
        layout.setHorizontalGroup(
                layout.createParallelGroup(GroupLayout.Alignment.CENTER)
                        .addGroup(layout.createSequentialGroup()
                                .addComponent(filechooseBtn)
                                .addComponent(captureBtn)
                                .addComponent(stopBtn)
                                .addComponent(playBtn)
                                .addComponent(liveBox))
                        .addComponent(textArea)
                        .addComponent(scrollPane)
                        .addComponent(statusLabel)
                        .addComponent(langLabel)
        );
        layout.setVerticalGroup(
                layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                                .addComponent(filechooseBtn)
                                .addComponent(captureBtn)
                                .addComponent(stopBtn)
                                .addComponent(playBtn)
                                .addComponent(liveBox))
                        .addComponent(textArea)
                        .addComponent(scrollPane)
                        .addComponent(statusLabel)
                        .addComponent(langLabel)
        );
        setTitle("Capture/Recognize Demo");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize((int)(600*scale),(int)(400*scale));
        setVisible(true);
    }

    //This method captures audio input from a microphone and saves it in a ByteArrayOutputStream object.
    private void captureAudio(DuplexRecognitionSession session){
        try{
            //Get everything set up for capture
            audioFormat = getAudioFormat();
            DataLine.Info dataLineInfo = new DataLine.Info(TargetDataLine.class, audioFormat);
            targetDataLine = (TargetDataLine) AudioSystem.getLine(dataLineInfo);
            targetDataLine.open(audioFormat);
            targetDataLine.start();

            // Create a thread to capture the microphone data and start it running.
            // It will run until the Stop button is clicked.
            Thread captureThread = new Thread(new CaptureThread(session));
            captureThread.start();
        } catch (Exception e) {
            System.out.println(e);
            System.exit(0);
        }
    }

    private static AudioFormat getAudioFormat(){
        float sampleRate = 16000.0F;	//8000,11025,16000,22050,44100
        int sampleSizeInBits = 16;		//8,16
        int channels = 1;				//1,2
        boolean signed = true;			//true,false
        boolean bigEndian = false;		//true,false
        return new AudioFormat(
                sampleRate,
                sampleSizeInBits,
                channels,
                signed,
                bigEndian);
    }

    //This thread puts the captured audio in the ByteArrayOutputStream object, and optionally sends it
    //to the speech server for live recognition.
    class CaptureThread extends Thread{
        private DuplexRecognitionSession session;
        //An arbitrary-size temporary holding buffer
        CaptureThread (DuplexRecognitionSession session) {
            this.session=session;
        }

        byte tempBuffer[] = new byte[8000];
        public void run(){
            byteArrayOutputStream = new ByteArrayOutputStream();
            stopCapture = false;
            try {
                //Loop until stopCapture is set by another thread that services the Stop button.
                while(!stopCapture){
                    //Read data from the internal buffer of the data line.
                    int cnt = targetDataLine.read(tempBuffer, 0, tempBuffer.length);
                    if(cnt > 0){
                        byteArrayOutputStream.write(tempBuffer, 0, cnt);
                        if (liveBox.isSelected()) {
                            session.sendChunk(tempBuffer, false);
                        }
                    }
                }
                byteArrayOutputStream.close();
                if (liveBox.isSelected()) {
                    byte tmp[] = new byte[0];
                    session.sendChunk(tmp,  true);
                }
            } catch (Exception e) {
                System.out.println(e);
                System.exit(0);
            }
        }
    }
}
