package com.example.util;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.example.R;

public final class AppToast {
    private AppToast() {
    }

    public static void show(Context context, String message) {
        if (context == null) {
            return;
        }

        View toastView = LayoutInflater.from(context).inflate(R.layout.toast_app_message, null);
        TextView messageView = toastView.findViewById(R.id.tv_toast_message);
        messageView.setText(message);

        Toast toast = new Toast(context.getApplicationContext());
        toast.setDuration(Toast.LENGTH_SHORT);
        toast.setView(toastView);
        toast.show();
    }
}
