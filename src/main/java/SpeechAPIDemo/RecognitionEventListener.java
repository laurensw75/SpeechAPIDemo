package SpeechAPIDemo;

public interface RecognitionEventListener {

	void onRecognitionEvent(RecognitionEvent event);
	
	void onClose();
}
