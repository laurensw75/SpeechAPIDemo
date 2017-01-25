package SpeechAPIDemo;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
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

    private static float scale;

    private static VUMeter vumeter;
    private static JTextArea textArea;
    private static JTextArea resultArea;
    private static JTextArea ctmresultArea;
    private static JTabbedPane resultsArea;
    private static JScrollPane resultscrollPane;
    private static JScrollPane ctmresultscrollPane;

    boolean stopCapture = false;
    static boolean sendPong = false;
    static boolean workersAvailable = false;
    static boolean liveRecognition = false;
    static boolean fileRecognition = false;

    final static JButton filechooseBtn = new JButton("Select File");
    final static JButton liveBtn = new JButton("Start Live Recognition");
    final Font defaultFont;

    private static JLabel statusLabel = new JLabel("");
    private static JLabel langLabel = new JLabel("");

    AudioFormat audioFormat;
    TargetDataLine targetDataLine;

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
                if(!liveRecognition && !fileRecognition) {
                    filechooseBtn.setEnabled(true);
                    liveBtn.setEnabled(true);
                }
                statusLabel.setText("Available slots: "+count);
            } else if (count == 0) {
                workersAvailable = false;
                filechooseBtn.setEnabled(false);
                if(!liveRecognition) {
                    liveBtn.setEnabled(false);
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
                // resultArea.append(event.getResult().getHypotheses().get(0).getTranscript() + " (" + String.format("%.3f", event.getResult().getHypotheses().get(0).getConfidence()) + ")\n");
                resultArea.append(event.getResult().getHypotheses().get(0).getTranscript() +"\n");
                ctmresultArea.append(event.getResult().getHypotheses().get(0).getCtmline());
                textArea.setText("");
                // System.err.println("IDX: " + resultsArea.getSelectedIndex());
                if(resultsArea.getSelectedIndex() == 0 ) {
                    resultArea.update(resultArea.getGraphics());
                    if (!resultscrollPane.getVerticalScrollBar().getValueIsAdjusting()) {
                        resultscrollPane.getVerticalScrollBar().setValue(resultscrollPane.getVerticalScrollBar().getMaximum());
                    }
                    resultArea.update(resultArea.getGraphics());
                } else if (resultsArea.getSelectedIndex() == 1 ) {
                    ctmresultArea.update(ctmresultArea.getGraphics());
                    if (!ctmresultscrollPane.getVerticalScrollBar().getValueIsAdjusting()) {
                        ctmresultscrollPane.getVerticalScrollBar().setValue(ctmresultscrollPane.getVerticalScrollBar().getMaximum());
                    }
                    ctmresultArea.update(ctmresultArea.getGraphics());
                }

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

        fileRecognition=false;
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
                Thread.sleep(50);
            }
            in.close();
        } catch (Exception e) {
            System.out.println("File could not be processed " + e);
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

    public void setFileChooserFont(Component[] comp, Font font) {
        for(int x = 0; x < comp.length; x++)
        {
            if(comp[x] instanceof Container) setFileChooserFont(((Container)comp[x]).getComponents(), font);
            try{comp[x].setFont(font);}
            catch(Exception e){}//do nothing
        }
    }

    class SaveActionListener implements ActionListener {
        public void actionPerformed(ActionEvent actionEvent) {
            if (actionEvent.getActionCommand().equals("Save text...") || actionEvent.getActionCommand().equals("Save ctm...")) {
                JFileChooser fc = new JFileChooser();
                setFileChooserFont(fc.getComponents(), defaultFont);
                fc.setPreferredSize(new Dimension((int) scale * 600, (int) scale * 400));
                int returnVal = fc.showSaveDialog(getParent());
                if (returnVal == JFileChooser.APPROVE_OPTION) {
                    try {
                        FileWriter fw = new FileWriter(fc.getSelectedFile().getAbsoluteFile());
                        if (actionEvent.getActionCommand().equals("Save text...")) {
                            fw.write(resultArea.getText().toString());
                        } else {
                            fw.write(ctmresultArea.getText().toString());
                        }
                        fw.close();
                    } catch (IOException e2) {
                        System.err.println("Caught Exception: " + e2.getMessage());
                    }
                }
                System.out.println("Selected: " + actionEvent.getActionCommand());
            }
        }
    }

    public ExampleApp() {
        scale = 1;
        int screenwidth = (int)Toolkit.getDefaultToolkit().getScreenSize().getWidth();
        // This is an ugly hack, but the dpi value we receive on hidpi screens is wrong :(
        if (screenwidth > 3000) {
            scale=2;
        }

        int fontsize=(int)(16*scale);
        defaultFont=new Font("Arial", Font.PLAIN, fontsize);
        final Font fixedFont=new Font("Courier", Font.PLAIN, fontsize);
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
        liveBtn.setFont(defaultFont);

        statusLabel.setFont(defaultFont);
        langLabel.setFont(defaultFont);

        filechooseBtn.setEnabled(true);
        liveBtn.setEnabled(true);

        filechooseBtn.addActionListener(
                new ActionListener(){
                    public void actionPerformed(ActionEvent e){
                        liveBtn.setEnabled(false);
                        filechooseBtn.setEnabled(false);
                        fileRecognition=true;
                        JFileChooser fc = new JFileChooser();
                        setFileChooserFont(fc.getComponents(), defaultFont);
                        fc.setPreferredSize(new Dimension((int)scale*600, (int)scale*400));
                        int returnVal = fc.showOpenDialog(getParent());
                        if(returnVal == JFileChooser.APPROVE_OPTION) {
                            try {
                                testRecognition(fc.getSelectedFile().getAbsoluteFile());
                            } catch (IOException|URISyntaxException|InterruptedException e2 ) {
                                System.err.println("Caught Exception: " + e2.getMessage());
                            }
                        } else {
                            liveBtn.setEnabled(true);
                            filechooseBtn.setEnabled(true);
                            fileRecognition=false;

                        }
                    }
                }
        );

        //Register anonymous listeners
        liveBtn.addActionListener(
                new ActionListener(){
                    public void actionPerformed(ActionEvent e){
                        if(liveRecognition) {
                            liveRecognition=false;
                            stopCapture = true;
                            targetDataLine.close();
                            filechooseBtn.setEnabled(true);
                            liveBtn.setText("Start Live Recognition");
                        } else {
                            liveRecognition=true;
                            liveBtn.setText("Stop Live Recognition");
                            filechooseBtn.setEnabled(false);
                            WsDuplexRecognitionSession session = null;
                            try {
                                RecognitionEventAccumulator eventAccumulator = new RecognitionEventAccumulator();
                                session = new WsDuplexRecognitionSession(DEFAULT_WS_URL);
                                session.addRecognitionEventListener(eventAccumulator);
                                session.setUserId(UserID);
                                session.setContentId(ContentID);
                                session.connect();
                            } catch (Exception e2) {
                                System.err.println("Caught Exception: " + e2.getMessage());
                            }
                            captureAudio(session);
                        }
                    }
                }
        );

        textArea = new JTextArea(3,30);
        resultArea = new JTextArea(10,30);
        final JPopupMenu saveTxtMenu = new JPopupMenu();
        ActionListener saveListener = new SaveActionListener();
        JMenuItem saveTxtItem = new JMenuItem("Save text...");
        saveTxtItem.addActionListener(saveListener);
        saveTxtMenu.add(saveTxtItem);
        saveTxtItem.setFont(defaultFont);
        resultArea.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                super.mousePressed(e);
                saveTxtMenu.show(e.getComponent(), e.getX(), e.getY());
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                super.mouseReleased(e);
                saveTxtMenu.setVisible(false);
            }
        });
        ctmresultArea = new JTextArea(10,30);
        final JPopupMenu saveCtmMenu = new JPopupMenu();
        JMenuItem saveCtmItem = new JMenuItem("Save ctm...");
        saveCtmItem.addActionListener(saveListener);
        saveCtmMenu.add(saveCtmItem);
        saveCtmItem.setFont(defaultFont);
        ctmresultArea.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                super.mousePressed(e);
                saveCtmMenu.show(e.getComponent(), e.getX(), e.getY());
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                super.mouseReleased(e);
                saveCtmMenu.setVisible(false);
            }
        });

        vumeter= new VUMeter();
        vumeter.setSize(100,10);

        resultscrollPane = new JScrollPane(resultArea);
        ctmresultscrollPane = new JScrollPane(ctmresultArea);
        textArea.setEditable(false);
        textArea.setLineWrap(true);
        textArea.setFont(defaultFont);
        resultArea.setEditable(false);
        resultArea.setLineWrap(true);
        resultArea.setFont(defaultFont);
        ctmresultArea.setEditable(false);
        ctmresultArea.setLineWrap(true);
        ctmresultArea.setFont(fixedFont);

        resultsArea = new JTabbedPane();
        resultsArea.addTab("txt", resultscrollPane);
        resultsArea.addTab("ctm", ctmresultscrollPane);
        resultsArea.setTabPlacement(JTabbedPane.BOTTOM);
        resultsArea.setFont(defaultFont);

        GroupLayout layout=new GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setAutoCreateGaps(true);
        layout.setAutoCreateContainerGaps(true);
        layout.setHorizontalGroup(
                layout.createParallelGroup(GroupLayout.Alignment.CENTER)
                        .addGroup(layout.createSequentialGroup()
                                .addComponent(filechooseBtn)
                                .addComponent(liveBtn)
                                .addComponent(vumeter))
                        .addComponent(textArea)
                        .addComponent(resultsArea)
                        .addComponent(statusLabel)
                        .addComponent(langLabel)
        );
        layout.setVerticalGroup(
                layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                                .addComponent(filechooseBtn)
                                .addComponent(liveBtn)
                                .addComponent(vumeter))
                        .addComponent(textArea)
                        .addComponent(resultsArea)
                        .addComponent(statusLabel)
                        .addComponent(langLabel)
        );

        setTitle("Online Recognizer Demo");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize((int)(600*scale),(int)(400*scale));
        setVisible(true);
        vumeter.setlevel(0f);

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

    float getLevel(byte[] buffer) {
        int max=0;
        for (int i=0; i<buffer.length; i+=16) {
            short shortVal = (short) buffer[i+1];
            shortVal = (short) ((shortVal << 8) | buffer [i]);
            max=Math.max(max, (int) shortVal);
        }
        return (float) max / Short.MAX_VALUE;
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
            stopCapture = false;
            try {
                //Loop until stopCapture is set by another thread that services the Stop button.
                while(!stopCapture){
                    //Read data from the internal buffer of the data line.
                    int cnt = targetDataLine.read(tempBuffer, 0, tempBuffer.length);
                    if(cnt > 0){
                        session.sendChunk(tempBuffer, false);
                        double level=Math.log10(getLevel(tempBuffer));
                        // System.out.println("VU level: " + 20* level + " dB");
                        vumeter.setlevel((float) (level+1));
                    }
                    if (sendPong) {
                        sendPong = false;
                        session.sendChunk("PONG".getBytes(), false);
                    }

                }
                byte tmp[] = new byte[0];
                session.sendChunk(tmp,  true);
            } catch (Exception e) {
                System.out.println(e);
                System.exit(0);
            }
        }
    }
}
