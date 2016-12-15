package SpeechAPIDemo;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.*;
import javax.swing.*;

import java.awt.*;
import java.awt.event.*;
import javax.sound.sampled.*;

import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import java.net.URI;
import java.util.List;

public class ExampleApp extends JFrame {

    private static final long serialVersionUID = 1L;

    private static JTextArea textArea;
    private static JTextArea resultArea;

    boolean stopCapture = false;
    static boolean sendPong = false;
    static boolean bufferFilled = false;
    static boolean workersAvailable = false;

    final static JButton filechooseBtn = new JButton("Select File");
    final static JButton captureBtn = new JButton("Capture");
    final static JButton stopBtn = new JButton("Stop");
    final static JButton playBtn = new JButton("Recognize");
    final static JCheckBox liveBox = new JCheckBox("Live Recognition");

    private static JLabel statusLabel = new JLabel("");
    private static JLabel langLabel = new JLabel("");

    static ByteArrayOutputStream byteArrayOutputStream;
    AudioFormat audioFormat;
    TargetDataLine targetDataLine;
    static AudioInputStream audioInputStream;

    private static String DEFAULT_WS_URL;
    private static String DEFAULT_WS_STATUS_URL;
    private static String UserID = "unknown";
    private static String ContentID = "unknown";

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
                statusLabel.setText("Available slots: "+count);
            } else if (count == 0) {
                workersAvailable = false;
                filechooseBtn.setEnabled(false);
                playBtn.setEnabled(false);
                if (liveBox.isSelected()) {
                    captureBtn.setEnabled(false);
                }
                statusLabel.setText("No slots available!");
            } else {
                statusLabel.setText("Could not connect to server!");
                JOptionPane.showMessageDialog(null,
                        "\nCould not connect to server:\n\n"+DEFAULT_WS_STATUS_URL+"\n\n",
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
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
            System.err.println("Got event: " + event);
            if (event.getResult() != null) {
                textArea.setText(event.getResult().getHypotheses().get(0).getTranscript());
                textArea.update(textArea.getGraphics());
            }
            //
            if (event.getStatus() == RecognitionEvent.STATUS_PING) {
                System.err.println("Got Ping Request");
                sendPong=true;
            }
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
        session.setUserId(UserID);
        session.setContentId(ContentID);
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
        AudioInputStream in=null;
        AudioInputStream din=null;
        try {
            in = AudioSystem.getAudioInputStream(file);
            din = AudioSystem.getAudioInputStream(getAudioFormat(), in);

            int chunksPerSecond = 4;

            byte buf[] = new byte[bytesPerSecond / chunksPerSecond];

            while (true) {
                long millisWithinChunkSecond = System.currentTimeMillis() % (1000 / chunksPerSecond);
                int size = din.read(buf);
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
                // Thread.sleep(1000 / chunksPerSecond - millisWithinChunkSecond);
            }
            in.close();
        } catch (Exception e) {
            System.out.println("File could not be processed " + e);
        }
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
        if ((args.length>0) && (args[0].matches("^.*:[0-9]+$"))) {
            DEFAULT_WS_URL = "ws://"+args[0]+"/client/ws/speech";
            DEFAULT_WS_STATUS_URL = "ws://"+args[0]+"/client/ws/status";
            if (args.length>1) {
                UserID=args[1];
            }
            if (args.length>2) {
                ContentID=args[2];
            }
            new ExampleApp();
        } else {
            System.out.println("Please specify server and port to use (this.is.my.server:0000)");
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

    public ExampleApp() {
        float scale = 1;
        int screenwidth = (int)Toolkit.getDefaultToolkit().getScreenSize().getWidth();
        // This is an ugly hack, but the dpi value on hidpi screens is wrong :(
        if (screenwidth > 3000) {
            scale=2;
        }

        int fontsize=(int)(16*scale);
        Font defaultFont=new Font("Arial", Font.PLAIN, fontsize);
        javax.swing.UIManager.put("OptionPane.buttonFont", defaultFont);
        javax.swing.UIManager.put("OptionPane.messageFont", defaultFont);

        try {
            RecognitionEventAccumulator statusEventAccumulator = new RecognitionEventAccumulator();
            URI statusUri = new URI(DEFAULT_WS_STATUS_URL);
            WorkerCountClient status_session = new WorkerCountClient(statusUri,statusEventAccumulator);
            status_session.connect();
        } catch (Exception e3) {
            System.err.println("Caught Exception: " + e3.getMessage());
        }

        filechooseBtn.setFont(defaultFont);
        captureBtn.setFont(defaultFont);
        stopBtn.setFont(defaultFont);
        playBtn.setFont(defaultFont);
        liveBox.setFont(defaultFont);

        statusLabel.setFont(defaultFont);
        langLabel.setFont(defaultFont);

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
                                session.setUserId(UserID);
                                session.setContentId(ContentID);
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
        textArea.setFont(defaultFont);
        resultArea.setEditable(false);
        resultArea.setLineWrap(true);
        resultArea.setFont(defaultFont);

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
                    if (sendPong) {
                        sendPong = false;
                        session.sendChunk("PONG".getBytes(), false);
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
