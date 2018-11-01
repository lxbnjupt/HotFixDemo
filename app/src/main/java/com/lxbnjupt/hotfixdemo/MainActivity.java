package com.lxbnjupt.hotfixdemo;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import java.io.File;

public class MainActivity extends AppCompatActivity {

    private static final String DEX_DIR = "patch";
    private Button btnRun;
    private Button btnRepair;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnRun = (Button) findViewById(R.id.btn_run);
        btnRepair = (Button) findViewById(R.id.btn_repair);

        init();
        setOnClickListener();
    }

    private void init() {
        // 补丁存放目录为 /storage/emulated/0/Android/data/com.lxbnjupt.hotfixdemo/files/patch
        File patchDir = new File(this.getExternalFilesDir(null), DEX_DIR);
        if (!patchDir.exists()) {
            patchDir.mkdirs();
        }
    }

    private void setOnClickListener() {
        btnRun.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new BugTest().getBug();
            }
        });

        btnRepair.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    new HotFixUtils().doHotFix(MainActivity.this);
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                } catch (NoSuchFieldException e) {
                    e.printStackTrace();
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }
            }
        });
    }
}
