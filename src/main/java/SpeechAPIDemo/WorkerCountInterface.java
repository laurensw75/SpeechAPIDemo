package SpeechAPIDemo;

/**
 * Created by laurensw on 13-12-16.
 */
interface WorkerCountInterface {
    void notifyWorkerCount(int count);
    void notifyRequests(int count);
    void notifyDescription(String description);
}
