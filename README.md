# SpeechAPIDemo
JAVA application demonstrating the use of the net-speech-api for kaldigstserver.

Large parts of the code are based on https://github.com/Kaljurand/net-speech-api, and this code 
can be used as a starting point for a client using kaldigstserver (https://github.com/alumae/kaldi-gstreamer-server).

You can compile this program using:

`$ mvn package`

And then run with:

`$ java -jar target/SpeechAPIDemo.jar server:port [userid] [contentid]`

The server is assumed to be running on ws://server:port/client/ws/speech, with status info on ws://server:port/client/ws/status.


The main screen contains 4 buttons and a checkbox. Use 'Select File' to transcribe speech from a file, 'Capture' to record using a microphone, 'Stop' to stop recording, and 'Recognize' to perform ASR on the audio recorded with 'Capture'. 
The 'Live Recognition' checkbox enables immediate results as soon as 'Capture' is started.
