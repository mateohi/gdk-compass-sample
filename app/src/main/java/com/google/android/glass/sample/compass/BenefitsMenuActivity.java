package com.google.android.glass.sample.compass;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuItem;

import java.lang.Runnable;

public class BenefitsMenuActivity extends Activity {

    private final Handler handler = new Handler();

    private BenefitsService.BenefitsBinder benefitsBinder;
    private boolean attachedToWindow;
    private boolean optionsMenuOpen;

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            if (service instanceof BenefitsService.BenefitsBinder) {
                benefitsBinder = (BenefitsService.BenefitsBinder) service;
                openOptionsMenu();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            // Do nothing.
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        bindService(new Intent(this, BenefitsService.class), serviceConnection, 0);
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        attachedToWindow = true;
        openOptionsMenu();
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        attachedToWindow = false;
    }

    @Override
    public void openOptionsMenu() {
        if (!optionsMenuOpen && attachedToWindow && benefitsBinder != null) {
            super.openOptionsMenu();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.compass, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.read_aloud:
                benefitsBinder.readBenefitDescription();
                return true;
            case R.id.stop:
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        stopService(new Intent(BenefitsMenuActivity.this, BenefitsService.class));
                    }
                });
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onOptionsMenuClosed(Menu menu) {
        super.onOptionsMenuClosed(menu);
        optionsMenuOpen = false;

        unbindService(serviceConnection);
        finish();
    }
}
