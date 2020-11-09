package com.parabbits.tajniakiserver.shared.timer;


import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.util.TimerTask;

public class Timer {

    private boolean running = false;
    private final ITimerCallback callback;
    private java.util.Timer timer;

    public boolean isRunning(){
        return running;
    }

    public Timer(@NotNull ITimerCallback callback){
        this.callback = callback;
    }

    public void start(long time) {
        timer = new java.util.Timer();
        TimerTask timerTask = new CountDownTimerTask(time, timer, callback);
        timer.schedule(timerTask, 0, 1000);
        running = true;
    }

    public void stop(){
        timer.cancel();
        running = false;
    }

    private static class CountDownTimerTask extends TimerTask{

        private long remainigTime;
        private java.util.Timer timer;
        private ITimerCallback callback;

        CountDownTimerTask(long time, java.util.Timer timer, ITimerCallback callback){
            this.remainigTime = time;
            this.timer = timer;
            this.callback = callback;
        }

        @Override
        public void run() {
            remainigTime--;
            if (remainigTime == 0) {
                timer.cancel();
                timer.purge();
                try {
                    callback.onFinish();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                System.out.println("Zadanie wykonane");
            } else {
                callback.onTick(remainigTime);
                System.out.println("Beep");
                // TODO: tutaj można dodać jakiegoś klika
            }
        }
    }
}
