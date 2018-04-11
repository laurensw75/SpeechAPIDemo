package SpeechAPIDemo;

import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import javax.sound.sampled.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import org.concentus.*;
import org.gagravarr.ogg.*;
import org.gagravarr.opus.*;

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
    final static String [] compressionString = {"off (256kb/s)", "128kb/s", "64kb/s", "32kb/s", "16kb/s"};
    final static JComboBox compression = new JComboBox(compressionString);
    final Font defaultFont;

    private static JLabel compressionLabel = new JLabel ("Opus Compression");
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
        try {
            FileInputStream in = new FileInputStream(file);
            int chunkSize = 8000;
            int waittime=(int)(((double)chunkSize/bytesPerSecond)*1000);
            byte buf[] = new byte[chunkSize];
            int size;
            long lasttime=System.currentTimeMillis();

            while (true) {
                size = in.read(buf);
                if (size > 0) {
                    session.sendChunk(buf, false);
                    Thread.sleep(waittime-(int)(System.currentTimeMillis()-lasttime));
                    lasttime=System.currentTimeMillis();
                } else {
                    byte buf2[] = new byte[0];
                    session.sendChunk(buf2, true);
                    break;
                }
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

        compression.setFont(defaultFont);
        compressionLabel.setFont(defaultFont);
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
                            compression.setEnabled(true);
                            liveBtn.setText("Start Live Recognition");
                        } else {
                            liveRecognition=true;
                            liveBtn.setText("Stop Live Recognition");
                            filechooseBtn.setEnabled(false);
                            compression.setEnabled(false);
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
                        .addGroup(layout.createSequentialGroup()
                                .addComponent(compressionLabel)
                                .addComponent(compression))
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
                        .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                                .addComponent(compressionLabel)
                                .addComponent(compression))
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

        byte tempBuffer[] = new byte[1920];
        byte data_packet[] = new byte[1275];
        int bitrate=0;
        int packetSamples=960;
        OpusEncoder encoder;
        OpusFile file;
        OpusInfo info;
        OggPacketWriter OPwriter;
;
        ByteArrayOutputStream BAOut;


        public void run(){
            stopCapture = false;

            String compressionSelect=compression.getSelectedItem().toString();
            if (compressionSelect==compressionString[1]) {
                bitrate=128000;
            } else if (compressionSelect==compressionString[2]) {
                bitrate=64000;
            } else if (compressionSelect==compressionString[3]) {
                bitrate=32000;
            } else if (compressionSelect==compressionString[4]) {
                bitrate=16000;
            }

            System.out.println("Bitrate: " + bitrate + " kbit/second");

            try {
                if (bitrate==0) {
                    //With no compression we send pcm data. To instruct our decoder, we make a wav header first.
                    session.sendChunk(CreateWAVHeader((int) audioFormat.getSampleRate(), audioFormat.getChannels(), audioFormat.getSampleSizeInBits()), false);
                } else {
                    // for an opus encoded stream we need to create the opusencoder, and also setup the stream writer
                    encoder = new OpusEncoder(16000, 1, OpusApplication.OPUS_APPLICATION_AUDIO);
                    encoder.setBitrate(bitrate);
                    encoder.setSignalType(OpusSignal.OPUS_SIGNAL_MUSIC);
                    encoder.setComplexity(10);
                    encoder.setUseVBR(true);
                    BAOut = new ByteArrayOutputStream();

                    info = new OpusInfo();
                    info.setNumChannels(1);
                    info.setSampleRate(16000);
                    OpusTags tags = new OpusTags();
                    file = new OpusFile(BAOut, info, tags);
                    OPwriter = file.getOggFile().getPacketWriter();
                    OPwriter.bufferPacket(info.write(), false);
                    OPwriter.bufferPacket(tags.write(), false);
                }

                int written=0;
                //Loop until stopCapture is set by another thread that services the Stop button.
                while(!stopCapture){
                    //Read data from the internal buffer of the data line.
                    int cnt = targetDataLine.read(tempBuffer, 0, tempBuffer.length);
                    if(cnt > 0) {
                        if (bitrate>0) {
                            // let's use encryption
                            short[] pcm = BytesToShorts(tempBuffer, 0, tempBuffer.length);
                            int bytesEncoded = encoder.encode(pcm, 0, packetSamples, data_packet, 0, 1275);
                            byte[] packet = new byte[bytesEncoded];
                            System.arraycopy(data_packet, 0, packet, 0, bytesEncoded);
                            OpusAudioData data = new OpusAudioData(packet);

                            OPwriter.bufferPacket(data.write(), true);
                            // let's send it out in chunks of ~0.25sec. VBR will cause the chunks to typically be a bit larger.
                            if (BAOut.size()>(bitrate/32)) {
                                session.sendChunk(BAOut.toByteArray(), false);
                                BAOut.reset();
                            }

                            // System.out.println("bytesEncoded: " +bytesEncoded);
                        } else {
                            session.sendChunk(tempBuffer, false);
                        }

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

    public static short[] BytesToShorts(byte[] input) {
        return BytesToShorts(input, 0, input.length);
    }

    public static short[] BytesToShorts(byte[] input, int offset, int length) {
        short[] processedValues = new short[length / 2];
        for (int c = 0; c < processedValues.length; c++) {
            short a = (short) (((int) input[(c * 2) + offset]) & 0xFF);
            short b = (short) (((int) input[(c * 2) + 1 + offset]) << 8);
            processedValues[c] = (short) (a | b);
        }

        return processedValues;
    }

    public static byte[] ShortsToBytes(short[] input) {
        return ShortsToBytes(input, 0, input.length);
    }

    public static byte[] ShortsToBytes(short[] input, int offset, int length) {
        byte[] processedValues = new byte[length * 2];
        for (int c = 0; c < length; c++) {
            processedValues[c * 2] = (byte) (input[c + offset] & 0xFF);
            processedValues[c * 2 + 1] = (byte) ((input[c + offset] >> 8) & 0xFF);
        }

        return processedValues;
    }

    public static byte[] CreateWAVHeader(int samplerate, int channels, int format) {
        byte[] header = new byte[44];
        long totalDataLen = 36;
        long bitrate = samplerate*channels*format;
        header[0] = 'R';
        header[1] = 'I';
        header[2] = 'F';
        header[3] = 'F';
        header[4] = (byte) (totalDataLen & 0xff);
        header[5] = (byte) ((totalDataLen >> 8) & 0xff);
        header[6] = (byte) ((totalDataLen >> 16) & 0xff);
        header[7] = (byte) ((totalDataLen >> 24) & 0xff);
        header[8] = 'W';
        header[9] = 'A';
        header[10] = 'V';
        header[11] = 'E';
        header[12] = 'f';
        header[13] = 'm';
        header[14] = 't';
        header[15] = ' ';
        header[16] = (byte) format;
        header[17] = 0;
        header[18] = 0;
        header[19] = 0;
        header[20] = 1;
        header[21] = 0;
        header[22] = (byte) channels;
        header[23] = 0;
        header[24] = (byte) (samplerate & 0xff);
        header[25] = (byte) ((samplerate >> 8) & 0xff);
        header[26] = (byte) ((samplerate >> 16) & 0xff);
        header[27] = (byte) ((samplerate >> 24) & 0xff);
        header[28] = (byte) ((bitrate / 8) & 0xff);
        header[29] = (byte) (((bitrate / 8) >> 8) & 0xff);
        header[30] = (byte) (((bitrate / 8) >> 16) & 0xff);
        header[31] = (byte) (((bitrate / 8) >> 24) & 0xff);
        header[32] = (byte) ((channels* format) / 8);
        header[33] = 0;
        header[34] = 16;
        header[35] = 0;
        header[36] = 'd';
        header[37] = 'a';
        header[38] = 't';
        header[39] = 'a';
        header[40] = (byte) (0  & 0xff);
        header[41] = (byte) ((0 >> 8) & 0xff);
        header[42] = (byte) ((0 >> 16) & 0xff);
        header[43] = (byte) ((0 >> 24) & 0xff);

        return header;
    }

}
