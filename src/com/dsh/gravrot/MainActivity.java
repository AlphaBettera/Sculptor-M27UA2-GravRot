package com.dsh.gravrot;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity {

    private TextView statusView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        float density = getResources().getDisplayMetrics().density;
        int pad = (int) (20 * density);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("GravRot — 重力旋转修正");
        title.setTextSize(20);
        root.addView(title);

        TextView info = new TextView(this);
        info.setText("本机加速度计绕垂直轴装反了 180°，横屏正常、竖屏必错。"
                + "此服务读取加速度计并修正后写回旋转锁定，开机自动运行。");
        info.setTextSize(13);
        info.setPadding(0, pad / 2, 0, pad / 2);
        root.addView(info);

        statusView = new TextView(this);
        statusView.setTextSize(14);
        statusView.setPadding(0, pad / 2, 0, pad / 2);
        root.addView(statusView);

        Button grant = new Button(this);
        grant.setText("1. 授予「修改系统设置」权限");
        grant.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent i = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
                i.setData(Uri.parse("package:" + getPackageName()));
                try {
                    startActivity(i);
                } catch (Exception e) {
                    startActivity(new Intent(Settings.ACTION_SETTINGS));
                }
            }
        });
        root.addView(grant);

        Button start = new Button(this);
        start.setText("2. 启动服务");
        start.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startServiceInternal();
                refresh();
            }
        });
        root.addView(start);

        Button stop = new Button(this);
        stop.setText("停止服务");
        stop.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                stopService(new Intent(MainActivity.this, GravRotService.class));
                refresh();
            }
        });
        root.addView(stop);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);

        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Settings.System.canWrite(this)) {
            startServiceInternal();
        }
        refresh();
    }

    private void startServiceInternal() {
        Intent service = new Intent(this, GravRotService.class);
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(service);
        } else {
            startService(service);
        }
    }

    private void refresh() {
        boolean can = Settings.System.canWrite(this);
        int userRotation = Settings.System.getInt(
                getContentResolver(), Settings.System.USER_ROTATION, -1);
        int autoRotation = Settings.System.getInt(
                getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, -1);

        statusView.setText("权限: " + (can ? "已授予 ✓" : "未授予 ✗")
                + "\n服务: " + (GravRotService.RUNNING ? "运行中 ✓" : "已停止")
                + "\nuser_rotation = " + userRotation
                + "\naccelerometer_rotation = " + autoRotation
                + (can ? "" : "\n\n请先点上面的按钮授予权限，否则无法修改旋转。"));
    }
}
