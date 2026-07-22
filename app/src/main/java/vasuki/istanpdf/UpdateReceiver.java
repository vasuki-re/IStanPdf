package vasuki.istanpdf;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class UpdateReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) {
            // Silently sync the launcher icon matching the saved accent after an app update
            ThemePrefs.applyLauncherIconSilent(context);
        }
    }
}
