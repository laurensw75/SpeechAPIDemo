# SpeechAPIDemo
JAVA application demonstrating the use of the net-speech-api for kaldigstserver.

Large parts of the code are based on https://github.com/Kaljurand/net-speech-api, and this code 
can be used as a starting point for a client using kaldigstserver (https://github.com/alumae/kaldi-gstreamer-server).

You can compile this program using:

`$ mvn package`

And then run with:

`$ java -jar target/SpeechAPIDemo.jar server:port [userid] [contentid]`


