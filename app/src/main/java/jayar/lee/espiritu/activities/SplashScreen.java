package jayar.lee.espiritu.activities;

import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Bundle;
import android.view.Window;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import com.jepongdevxyz.fablevpn.R;

public class SplashScreen extends AppCompatActivity
{

    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        Window window = getWindow();
        window.setFormat(PixelFormat.RGBA_8888);
    }

    int waited = -1;
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.splash);
        StartAnimations();
    }

    private void StartAnimations() {
        Animation animation = AnimationUtils.loadAnimation(getApplicationContext(), android.R.anim.fade_in);
        animation.reset();
        TextView appName=findViewById(R.id.splashTextView1);
        appName.clearAnimation();
        appName.startAnimation(animation);
        Animation anim = AnimationUtils.loadAnimation(getApplicationContext(), android.R.anim.fade_in);
        anim.reset();
        TextView l=findViewById(R.id.splashTextView2);
        l.clearAnimation();
        l.startAnimation(anim);
        Animation anims = AnimationUtils.loadAnimation(getApplicationContext(), android.R.anim.fade_in);
        anims.reset();
        anims = AnimationUtils.loadAnimation(getApplicationContext(),android.R.anim.fade_in);
        anims.reset();
        ImageView iv = findViewById(R.id.image_launcher);
        iv.clearAnimation();
        iv.startAnimation(anims);
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(new Runnable() {
                public void run(){
                    runOnUiThread(new Runnable() {
                            public void run(){
                                waited++;
                                if(waited==3){
                                    scheduler.shutdown();
                                    waited = 0;
                                    Intent intent = new Intent(SplashScreen.this,OpenVPNClient.class);
                                    intent.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
                                    startActivity(intent);
                                    SplashScreen.this.finish();
                                }
                            }
                    });
            }
        }, 0, 1, TimeUnit.SECONDS);
    }


}


