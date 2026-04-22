Windows PowerShell
Copyright (C) Microsoft Corporation. Tous droits réservés.

Installez la dernière version de PowerShell pour de nouvelles fonctionnalités et améliorations ! https://aka.ms/PSWindows

PS D:\Dev> & "C:\Users\Lenovo\AppData\Local\Android\Sdk\platform-tools\adb.exe" kill-server
PS D:\Dev> & "C:\Users\Lenovo\AppData\Local\Android\Sdk\platform-tools\adb.exe" start-server
* daemon not running; starting now at tcp:5037
* daemon started successfully
PS D:\Dev> & "C:\Users\Lenovo\AppData\Local\Android\Sdk\platform-tools\adb.exe" devices
List of devices attached

PS D:\Dev> & "C:\Users\Lenovo\AppData\Local\Android\Sdk\platform-tools\adb.exe" devices
List of devices attached
R5CWA0FEPZW     device

PS D:\Dev> & "C:\Users\Lenovo\AppData\Local\Android\Sdk\platform-tools\adb.exe" -s "R5CWA0FEPZW" logcat AndroidRuntime:E *:S
--------- beginning of crash
04-22 08:16:44.682 20260 20260 E AndroidRuntime: FATAL EXCEPTION: main
04-22 08:16:44.682 20260 20260 E AndroidRuntime: Process: com.swimvpn.app, PID: 20260
04-22 08:16:44.682 20260 20260 E AndroidRuntime: java.lang.RuntimeException: Unable to start activity ComponentInfo{com.swimvpn.app/com.swimvpn.app.MainActivity}: java.lang.IllegalStateException: You need to use a Theme.AppCompat theme (or descendant) with this activity.
04-22 08:16:44.682 20260 20260 E AndroidRuntime:        at android.app.ActivityThread.performLaunchActivity(ActivityThread.java:4710)
04-22 08:16:44.682 20260 20260 E AndroidRuntime:        at android.app.ActivityThread.handleLaunchActivity(ActivityThread.java:4941)
04-22 08:16:44.682 20260 20260 E AndroidRuntime:        at android.app.servertransaction.LaunchActivityItem.execute(LaunchActivityItem.java:222)
04-22 08:16:44.682 20260 20260 E AndroidRuntime:        at android.app.servertransaction.TransactionExecutor.executeNonLifecycleItem(TransactionExecutor.java:133)
04-22 08:16:44.682 20260 20260 E AndroidRuntime:        at android.app.servertransaction.TransactionExecutor.executeTransactionItems(TransactionExecutor.java:103)
04-22 08:16:44.682 20260 20260 E AndroidRuntime:        at android.app.servertransaction.TransactionExecutor.execute(TransactionExecutor.java:80)
04-22 08:16:44.682 20260 20260 E AndroidRuntime:        at android.app.ActivityThread$H.handleMessage(ActivityThread.java:3150)
04-22 08:16:44.682 20260 20260 E AndroidRuntime:        at android.os.Handler.dispatchMessage(Handler.java:110)
04-22 08:16:44.682 20260 20260 E AndroidRuntime:        at android.os.Looper.loopOnce(Looper.java:273)
04-22 08:16:44.682 20260 20260 E AndroidRuntime:        at android.os.Looper.loop(Looper.java:363)
04-22 08:16:44.682 20260 20260 E AndroidRuntime:        at android.app.ActivityThread.main(ActivityThread.java:10060)
04-22 08:16:44.682 20260 20260 E AndroidRuntime:        at java.lang.reflect.Method.invoke(Native Method)
04-22 08:16:44.682 20260 20260 E AndroidRuntime:        at com.android.internal.os.RuntimeInit$MethodAndArgsCaller.run(RuntimeInit.java:632)
04-22 08:16:44.682 20260 20260 E AndroidRuntime:        at com.android.internal.os.ZygoteInit.main(ZygoteInit.java:975)
04-22 08:16:44.682 20260 20260 E AndroidRuntime: Caused by: java.lang.IllegalStateException: You need to use a Theme.AppCompat theme (or descendant) with this activity.
04-22 08:16:44.682 20260 20260 E AndroidRuntime:        at androidx.appcompat.app.AppCompatDelegateImpl.createSubDecor(AppCompatDelegateImpl.java:902)
04-22 08:16:44.682 20260 20260 E AndroidRuntime:        at androidx.appcompat.app.AppCompatDelegateImpl.ensureSubDecor(AppCompatDelegateImpl.java:865)
04-22 08:16:44.682 20260 20260 E AndroidRuntime:        at androidx.appcompat.app.AppCompatDelegateImpl.setContentView(AppCompatDelegateImpl.java:757)
04-22 08:16:44.682 20260 20260 E AndroidRuntime:        at androidx.appcompat.app.AppCompatActivity.setContentView(AppCompatActivity.java:209)
04-22 08:16:44.682 20260 20260 E AndroidRuntime:        at androidx.activity.compose.ComponentActivityKt.setContent(ComponentActivity.kt:70)
04-22 08:16:44.682 20260 20260 E AndroidRuntime:        at androidx.activity.compose.ComponentActivityKt.setContent$default(ComponentActivity.kt:51)
04-22 08:16:44.682 20260 20260 E AndroidRuntime:        at com.swimvpn.app.MainActivity.onCreate(MainActivity.kt:105)
04-22 08:16:44.682 20260 20260 E AndroidRuntime:        at android.app.Activity.performCreate(Activity.java:9519)
04-22 08:16:44.682 20260 20260 E AndroidRuntime:        at android.app.Activity.performCreate(Activity.java:9488)
04-22 08:16:44.682 20260 20260 E AndroidRuntime:        at android.app.Instrumentation.callActivityOnCreate(Instrumentation.java:1524)
04-22 08:16:44.682 20260 20260 E AndroidRuntime:        at android.app.ActivityThread.performLaunchActivity(ActivityThread.java:4692)
04-22 08:16:44.682 20260 20260 E AndroidRuntime:        ... 13 more
04-22 08:16:48.897 20557 20557 E AndroidRuntime: FATAL EXCEPTION: main
04-22 08:16:48.897 20557 20557 E AndroidRuntime: Process: com.swimvpn.app, PID: 20557
04-22 08:16:48.897 20557 20557 E AndroidRuntime: java.lang.RuntimeException: Unable to start activity ComponentInfo{com.swimvpn.app/com.swimvpn.app.MainActivity}: java.lang.IllegalStateException: You need to use a Theme.AppCompat theme (or descendant) with this activity.
04-22 08:16:48.897 20557 20557 E AndroidRuntime:        at android.app.ActivityThread.performLaunchActivity(ActivityThread.java:4710)
04-22 08:16:48.897 20557 20557 E AndroidRuntime:        at android.app.ActivityThread.handleLaunchActivity(ActivityThread.java:4941)
04-22 08:16:48.897 20557 20557 E AndroidRuntime:        at android.app.servertransaction.LaunchActivityItem.execute(LaunchActivityItem.java:222)
04-22 08:16:48.897 20557 20557 E AndroidRuntime:        at android.app.servertransaction.TransactionExecutor.executeNonLifecycleItem(TransactionExecutor.java:133)
04-22 08:16:48.897 20557 20557 E AndroidRuntime:        at android.app.servertransaction.TransactionExecutor.executeTransactionItems(TransactionExecutor.java:103)
04-22 08:16:48.897 20557 20557 E AndroidRuntime:        at android.app.servertransaction.TransactionExecutor.execute(TransactionExecutor.java:80)
04-22 08:16:48.897 20557 20557 E AndroidRuntime:        at android.app.ActivityThread$H.handleMessage(ActivityThread.java:3150)
04-22 08:16:48.897 20557 20557 E AndroidRuntime:        at android.os.Handler.dispatchMessage(Handler.java:110)
04-22 08:16:48.897 20557 20557 E AndroidRuntime:        at android.os.Looper.loopOnce(Looper.java:273)
04-22 08:16:48.897 20557 20557 E AndroidRuntime:        at android.os.Looper.loop(Looper.java:363)
04-22 08:16:48.897 20557 20557 E AndroidRuntime:        at android.app.ActivityThread.main(ActivityThread.java:10060)
04-22 08:16:48.897 20557 20557 E AndroidRuntime:        at java.lang.reflect.Method.invoke(Native Method)
04-22 08:16:48.897 20557 20557 E AndroidRuntime:        at com.android.internal.os.RuntimeInit$MethodAndArgsCaller.run(RuntimeInit.java:632)
04-22 08:16:48.897 20557 20557 E AndroidRuntime:        at com.android.internal.os.ZygoteInit.main(ZygoteInit.java:975)
04-22 08:16:48.897 20557 20557 E AndroidRuntime: Caused by: java.lang.IllegalStateException: You need to use a Theme.AppCompat theme (or descendant) with this activity.
04-22 08:16:48.897 20557 20557 E AndroidRuntime:        at androidx.appcompat.app.AppCompatDelegateImpl.createSubDecor(AppCompatDelegateImpl.java:902)
04-22 08:16:48.897 20557 20557 E AndroidRuntime:        at androidx.appcompat.app.AppCompatDelegateImpl.ensureSubDecor(AppCompatDelegateImpl.java:865)
04-22 08:16:48.897 20557 20557 E AndroidRuntime:        at androidx.appcompat.app.AppCompatDelegateImpl.setContentView(AppCompatDelegateImpl.java:757)
04-22 08:16:48.897 20557 20557 E AndroidRuntime:        at androidx.appcompat.app.AppCompatActivity.setContentView(AppCompatActivity.java:209)
04-22 08:16:48.897 20557 20557 E AndroidRuntime:        at androidx.activity.compose.ComponentActivityKt.setContent(ComponentActivity.kt:70)
04-22 08:16:48.897 20557 20557 E AndroidRuntime:        at androidx.activity.compose.ComponentActivityKt.setContent$default(ComponentActivity.kt:51)
04-22 08:16:48.897 20557 20557 E AndroidRuntime:        at com.swimvpn.app.MainActivity.onCreate(MainActivity.kt:105)
04-22 08:16:48.897 20557 20557 E AndroidRuntime:        at android.app.Activity.performCreate(Activity.java:9519)
04-22 08:16:48.897 20557 20557 E AndroidRuntime:        at android.app.Activity.performCreate(Activity.java:9488)
04-22 08:16:48.897 20557 20557 E AndroidRuntime:        at android.app.Instrumentation.callActivityOnCreate(Instrumentation.java:1524)
04-22 08:16:48.897 20557 20557 E AndroidRuntime:        at android.app.ActivityThread.performLaunchActivity(ActivityThread.java:4692)
04-22 08:16:48.897 20557 20557 E AndroidRuntime:        ... 13 more
04-22 08:17:37.046 23027 23027 E AndroidRuntime: FATAL EXCEPTION: main
04-22 08:17:37.046 23027 23027 E AndroidRuntime: Process: com.swimvpn.app, PID: 23027
04-22 08:17:37.046 23027 23027 E AndroidRuntime: java.lang.RuntimeException: Unable to start activity ComponentInfo{com.swimvpn.app/com.swimvpn.app.MainActivity}: java.lang.IllegalStateException: You need to use a Theme.AppCompat theme (or descendant) with this activity.
04-22 08:17:37.046 23027 23027 E AndroidRuntime:        at android.app.ActivityThread.performLaunchActivity(ActivityThread.java:4710)
04-22 08:17:37.046 23027 23027 E AndroidRuntime:        at android.app.ActivityThread.handleLaunchActivity(ActivityThread.java:4941)
04-22 08:17:37.046 23027 23027 E AndroidRuntime:        at android.app.servertransaction.LaunchActivityItem.execute(LaunchActivityItem.java:222)
04-22 08:17:37.046 23027 23027 E AndroidRuntime:        at android.app.servertransaction.TransactionExecutor.executeNonLifecycleItem(TransactionExecutor.java:133)
04-22 08:17:37.046 23027 23027 E AndroidRuntime:        at android.app.servertransaction.TransactionExecutor.executeTransactionItems(TransactionExecutor.java:103)
04-22 08:17:37.046 23027 23027 E AndroidRuntime:        at android.app.servertransaction.TransactionExecutor.execute(TransactionExecutor.java:80)
04-22 08:17:37.046 23027 23027 E AndroidRuntime:        at android.app.ActivityThread$H.handleMessage(ActivityThread.java:3150)
04-22 08:17:37.046 23027 23027 E AndroidRuntime:        at android.os.Handler.dispatchMessage(Handler.java:110)
04-22 08:17:37.046 23027 23027 E AndroidRuntime:        at android.os.Looper.loopOnce(Looper.java:273)
04-22 08:17:37.046 23027 23027 E AndroidRuntime:        at android.os.Looper.loop(Looper.java:363)
04-22 08:17:37.046 23027 23027 E AndroidRuntime:        at android.app.ActivityThread.main(ActivityThread.java:10060)
04-22 08:17:37.046 23027 23027 E AndroidRuntime:        at java.lang.reflect.Method.invoke(Native Method)
04-22 08:17:37.046 23027 23027 E AndroidRuntime:        at com.android.internal.os.RuntimeInit$MethodAndArgsCaller.run(RuntimeInit.java:632)
04-22 08:17:37.046 23027 23027 E AndroidRuntime:        at com.android.internal.os.ZygoteInit.main(ZygoteInit.java:975)
04-22 08:17:37.046 23027 23027 E AndroidRuntime: Caused by: java.lang.IllegalStateException: You need to use a Theme.AppCompat theme (or descendant) with this activity.
04-22 08:17:37.046 23027 23027 E AndroidRuntime:        at androidx.appcompat.app.AppCompatDelegateImpl.createSubDecor(AppCompatDelegateImpl.java:902)
04-22 08:17:37.046 23027 23027 E AndroidRuntime:        at androidx.appcompat.app.AppCompatDelegateImpl.ensureSubDecor(AppCompatDelegateImpl.java:865)
04-22 08:17:37.046 23027 23027 E AndroidRuntime:        at androidx.appcompat.app.AppCompatDelegateImpl.setContentView(AppCompatDelegateImpl.java:757)
04-22 08:17:37.046 23027 23027 E AndroidRuntime:        at androidx.appcompat.app.AppCompatActivity.setContentView(AppCompatActivity.java:209)
04-22 08:17:37.046 23027 23027 E AndroidRuntime:        at androidx.activity.compose.ComponentActivityKt.setContent(ComponentActivity.kt:70)
04-22 08:17:37.046 23027 23027 E AndroidRuntime:        at androidx.activity.compose.ComponentActivityKt.setContent$default(ComponentActivity.kt:51)
04-22 08:17:37.046 23027 23027 E AndroidRuntime:        at com.swimvpn.app.MainActivity.onCreate(MainActivity.kt:105)
04-22 08:17:37.046 23027 23027 E AndroidRuntime:        at android.app.Activity.performCreate(Activity.java:9519)
04-22 08:17:37.046 23027 23027 E AndroidRuntime:        at android.app.Activity.performCreate(Activity.java:9488)
04-22 08:17:37.046 23027 23027 E AndroidRuntime:        at android.app.Instrumentation.callActivityOnCreate(Instrumentation.java:1524)
04-22 08:17:37.046 23027 23027 E AndroidRuntime:        at android.app.ActivityThread.performLaunchActivity(ActivityThread.java:4692)
04-22 08:17:37.046 23027 23027 E AndroidRuntime:        ... 13 more
04-22 08:27:18.755 29789 29789 E AndroidRuntime: FATAL EXCEPTION: main
04-22 08:27:18.755 29789 29789 E AndroidRuntime: Process: com.swimvpn.app, PID: 29789
04-22 08:27:18.755 29789 29789 E AndroidRuntime: java.lang.RuntimeException: Unable to start activity ComponentInfo{com.swimvpn.app/com.swimvpn.app.MainActivity}: java.lang.IllegalStateException: You need to use a Theme.AppCompat theme (or descendant) with this activity.
04-22 08:27:18.755 29789 29789 E AndroidRuntime:        at android.app.ActivityThread.performLaunchActivity(ActivityThread.java:4710)
04-22 08:27:18.755 29789 29789 E AndroidRuntime:        at android.app.ActivityThread.handleLaunchActivity(ActivityThread.java:4941)
04-22 08:27:18.755 29789 29789 E AndroidRuntime:        at android.app.servertransaction.LaunchActivityItem.execute(LaunchActivityItem.java:222)
04-22 08:27:18.755 29789 29789 E AndroidRuntime:        at android.app.servertransaction.TransactionExecutor.executeNonLifecycleItem(TransactionExecutor.java:133)
04-22 08:27:18.755 29789 29789 E AndroidRuntime:        at android.app.servertransaction.TransactionExecutor.executeTransactionItems(TransactionExecutor.java:103)
04-22 08:27:18.755 29789 29789 E AndroidRuntime:        at android.app.servertransaction.TransactionExecutor.execute(TransactionExecutor.java:80)
04-22 08:27:18.755 29789 29789 E AndroidRuntime:        at android.app.ActivityThread$H.handleMessage(ActivityThread.java:3150)
04-22 08:27:18.755 29789 29789 E AndroidRuntime:        at android.os.Handler.dispatchMessage(Handler.java:110)
04-22 08:27:18.755 29789 29789 E AndroidRuntime:        at android.os.Looper.loopOnce(Looper.java:273)
04-22 08:27:18.755 29789 29789 E AndroidRuntime:        at android.os.Looper.loop(Looper.java:363)
04-22 08:27:18.755 29789 29789 E AndroidRuntime:        at android.app.ActivityThread.main(ActivityThread.java:10060)
04-22 08:27:18.755 29789 29789 E AndroidRuntime:        at java.lang.reflect.Method.invoke(Native Method)
04-22 08:27:18.755 29789 29789 E AndroidRuntime:        at com.android.internal.os.RuntimeInit$MethodAndArgsCaller.run(RuntimeInit.java:632)
04-22 08:27:18.755 29789 29789 E AndroidRuntime:        at com.android.internal.os.ZygoteInit.main(ZygoteInit.java:975)
04-22 08:27:18.755 29789 29789 E AndroidRuntime: Caused by: java.lang.IllegalStateException: You need to use a Theme.AppCompat theme (or descendant) with this activity.
04-22 08:27:18.755 29789 29789 E AndroidRuntime:        at androidx.appcompat.app.AppCompatDelegateImpl.createSubDecor(AppCompatDelegateImpl.java:902)
04-22 08:27:18.755 29789 29789 E AndroidRuntime:        at androidx.appcompat.app.AppCompatDelegateImpl.ensureSubDecor(AppCompatDelegateImpl.java:865)
04-22 08:27:18.755 29789 29789 E AndroidRuntime:        at androidx.appcompat.app.AppCompatDelegateImpl.setContentView(AppCompatDelegateImpl.java:757)
04-22 08:27:18.755 29789 29789 E AndroidRuntime:        at androidx.appcompat.app.AppCompatActivity.setContentView(AppCompatActivity.java:209)
04-22 08:27:18.755 29789 29789 E AndroidRuntime:        at androidx.activity.compose.ComponentActivityKt.setContent(ComponentActivity.kt:70)
04-22 08:27:18.755 29789 29789 E AndroidRuntime:        at androidx.activity.compose.ComponentActivityKt.setContent$default(ComponentActivity.kt:51)
04-22 08:27:18.755 29789 29789 E AndroidRuntime:        at com.swimvpn.app.MainActivity.onCreate(MainActivity.kt:105)
04-22 08:27:18.755 29789 29789 E AndroidRuntime:        at android.app.Activity.performCreate(Activity.java:9519)
04-22 08:27:18.755 29789 29789 E AndroidRuntime:        at android.app.Activity.performCreate(Activity.java:9488)
04-22 08:27:18.755 29789 29789 E AndroidRuntime:        at android.app.Instrumentation.callActivityOnCreate(Instrumentation.java:1524)
04-22 08:27:18.755 29789 29789 E AndroidRuntime:        at android.app.ActivityThread.performLaunchActivity(ActivityThread.java:4692)
04-22 08:27:18.755 29789 29789 E AndroidRuntime:        ... 13 more
04-22 08:32:51.775  8956  8956 E AndroidRuntime: FATAL EXCEPTION: main
04-22 08:32:51.775  8956  8956 E AndroidRuntime: Process: com.swimvpn.app, PID: 8956
04-22 08:32:51.775  8956  8956 E AndroidRuntime: java.lang.RuntimeException: Unable to start activity ComponentInfo{com.swimvpn.app/com.swimvpn.app.MainActivity}: java.lang.IllegalStateException: You need to use a Theme.AppCompat theme (or descendant) with this activity.
04-22 08:32:51.775  8956  8956 E AndroidRuntime:        at android.app.ActivityThread.performLaunchActivity(ActivityThread.java:4710)
04-22 08:32:51.775  8956  8956 E AndroidRuntime:        at android.app.ActivityThread.handleLaunchActivity(ActivityThread.java:4941)
04-22 08:32:51.775  8956  8956 E AndroidRuntime:        at android.app.servertransaction.LaunchActivityItem.execute(LaunchActivityItem.java:222)
04-22 08:32:51.775  8956  8956 E AndroidRuntime:        at android.app.servertransaction.TransactionExecutor.executeNonLifecycleItem(TransactionExecutor.java:133)
04-22 08:32:51.775  8956  8956 E AndroidRuntime:        at android.app.servertransaction.TransactionExecutor.executeTransactionItems(TransactionExecutor.java:103)
04-22 08:32:51.775  8956  8956 E AndroidRuntime:        at android.app.servertransaction.TransactionExecutor.execute(TransactionExecutor.java:80)
04-22 08:32:51.775  8956  8956 E AndroidRuntime:        at android.app.ActivityThread$H.handleMessage(ActivityThread.java:3150)
04-22 08:32:51.775  8956  8956 E AndroidRuntime:        at android.os.Handler.dispatchMessage(Handler.java:110)
04-22 08:32:51.775  8956  8956 E AndroidRuntime:        at android.os.Looper.loopOnce(Looper.java:273)
04-22 08:32:51.775  8956  8956 E AndroidRuntime:        at android.os.Looper.loop(Looper.java:363)
04-22 08:32:51.775  8956  8956 E AndroidRuntime:        at android.app.ActivityThread.main(ActivityThread.java:10060)
04-22 08:32:51.775  8956  8956 E AndroidRuntime:        at java.lang.reflect.Method.invoke(Native Method)
04-22 08:32:51.775  8956  8956 E AndroidRuntime:        at com.android.internal.os.RuntimeInit$MethodAndArgsCaller.run(RuntimeInit.java:632)
04-22 08:32:51.775  8956  8956 E AndroidRuntime:        at com.android.internal.os.ZygoteInit.main(ZygoteInit.java:975)
04-22 08:32:51.775  8956  8956 E AndroidRuntime: Caused by: java.lang.IllegalStateException: You need to use a Theme.AppCompat theme (or descendant) with this activity.
04-22 08:32:51.775  8956  8956 E AndroidRuntime:        at androidx.appcompat.app.AppCompatDelegateImpl.createSubDecor(AppCompatDelegateImpl.java:902)
04-22 08:32:51.775  8956  8956 E AndroidRuntime:        at androidx.appcompat.app.AppCompatDelegateImpl.ensureSubDecor(AppCompatDelegateImpl.java:865)
04-22 08:32:51.775  8956  8956 E AndroidRuntime:        at androidx.appcompat.app.AppCompatDelegateImpl.setContentView(AppCompatDelegateImpl.java:757)
04-22 08:32:51.775  8956  8956 E AndroidRuntime:        at androidx.appcompat.app.AppCompatActivity.setContentView(AppCompatActivity.java:209)
04-22 08:32:51.775  8956  8956 E AndroidRuntime:        at androidx.activity.compose.ComponentActivityKt.setContent(ComponentActivity.kt:70)
04-22 08:32:51.775  8956  8956 E AndroidRuntime:        at androidx.activity.compose.ComponentActivityKt.setContent$default(ComponentActivity.kt:51)
04-22 08:32:51.775  8956  8956 E AndroidRuntime:        at com.swimvpn.app.MainActivity.onCreate(MainActivity.kt:105)
04-22 08:32:51.775  8956  8956 E AndroidRuntime:        at android.app.Activity.performCreate(Activity.java:9519)
04-22 08:32:51.775  8956  8956 E AndroidRuntime:        at android.app.Activity.performCreate(Activity.java:9488)
04-22 08:32:51.775  8956  8956 E AndroidRuntime:        at android.app.Instrumentation.callActivityOnCreate(Instrumentation.java:1524)
04-22 08:32:51.775  8956  8956 E AndroidRuntime:        at android.app.ActivityThread.performLaunchActivity(ActivityThread.java:4692)
04-22 08:32:51.775  8956  8956 E AndroidRuntime:        ... 13 more
04-22 09:11:16.175 23178 23178 E AndroidRuntime: FATAL EXCEPTION: main
04-22 09:11:16.175 23178 23178 E AndroidRuntime: Process: com.swimvpn.app, PID: 23178
04-22 09:11:16.175 23178 23178 E AndroidRuntime: java.lang.RuntimeException: Unable to start activity ComponentInfo{com.swimvpn.app/com.swimvpn.app.MainActivity}: java.lang.IllegalStateException: You need to use a Theme.AppCompat theme (or descendant) with this activity.
04-22 09:11:16.175 23178 23178 E AndroidRuntime:        at android.app.ActivityThread.performLaunchActivity(ActivityThread.java:4710)
04-22 09:11:16.175 23178 23178 E AndroidRuntime:        at android.app.ActivityThread.handleLaunchActivity(ActivityThread.java:4941)
04-22 09:11:16.175 23178 23178 E AndroidRuntime:        at android.app.servertransaction.LaunchActivityItem.execute(LaunchActivityItem.java:222)
04-22 09:11:16.175 23178 23178 E AndroidRuntime:        at android.app.servertransaction.TransactionExecutor.executeNonLifecycleItem(TransactionExecutor.java:133)
04-22 09:11:16.175 23178 23178 E AndroidRuntime:        at android.app.servertransaction.TransactionExecutor.executeTransactionItems(TransactionExecutor.java:103)
04-22 09:11:16.175 23178 23178 E AndroidRuntime:        at android.app.servertransaction.TransactionExecutor.execute(TransactionExecutor.java:80)
04-22 09:11:16.175 23178 23178 E AndroidRuntime:        at android.app.ActivityThread$H.handleMessage(ActivityThread.java:3150)
04-22 09:11:16.175 23178 23178 E AndroidRuntime:        at android.os.Handler.dispatchMessage(Handler.java:110)
04-22 09:11:16.175 23178 23178 E AndroidRuntime:        at android.os.Looper.loopOnce(Looper.java:273)
04-22 09:11:16.175 23178 23178 E AndroidRuntime:        at android.os.Looper.loop(Looper.java:363)
04-22 09:11:16.175 23178 23178 E AndroidRuntime:        at android.app.ActivityThread.main(ActivityThread.java:10060)
04-22 09:11:16.175 23178 23178 E AndroidRuntime:        at java.lang.reflect.Method.invoke(Native Method)
04-22 09:11:16.175 23178 23178 E AndroidRuntime:        at com.android.internal.os.RuntimeInit$MethodAndArgsCaller.run(RuntimeInit.java:632)
04-22 09:11:16.175 23178 23178 E AndroidRuntime:        at com.android.internal.os.ZygoteInit.main(ZygoteInit.java:975)
04-22 09:11:16.175 23178 23178 E AndroidRuntime: Caused by: java.lang.IllegalStateException: You need to use a Theme.AppCompat theme (or descendant) with this activity.
04-22 09:11:16.175 23178 23178 E AndroidRuntime:        at androidx.appcompat.app.AppCompatDelegateImpl.createSubDecor(AppCompatDelegateImpl.java:902)
04-22 09:11:16.175 23178 23178 E AndroidRuntime:        at androidx.appcompat.app.AppCompatDelegateImpl.ensureSubDecor(AppCompatDelegateImpl.java:865)
04-22 09:11:16.175 23178 23178 E AndroidRuntime:        at androidx.appcompat.app.AppCompatDelegateImpl.setContentView(AppCompatDelegateImpl.java:757)
04-22 09:11:16.175 23178 23178 E AndroidRuntime:        at androidx.appcompat.app.AppCompatActivity.setContentView(AppCompatActivity.java:209)
04-22 09:11:16.175 23178 23178 E AndroidRuntime:        at androidx.activity.compose.ComponentActivityKt.setContent(ComponentActivity.kt:70)
04-22 09:11:16.175 23178 23178 E AndroidRuntime:        at androidx.activity.compose.ComponentActivityKt.setContent$default(ComponentActivity.kt:51)
04-22 09:11:16.175 23178 23178 E AndroidRuntime:        at com.swimvpn.app.MainActivity.onCreate(MainActivity.kt:105)
04-22 09:11:16.175 23178 23178 E AndroidRuntime:        at android.app.Activity.performCreate(Activity.java:9519)
04-22 09:11:16.175 23178 23178 E AndroidRuntime:        at android.app.Activity.performCreate(Activity.java:9488)
04-22 09:11:16.175 23178 23178 E AndroidRuntime:        at android.app.Instrumentation.callActivityOnCreate(Instrumentation.java:1524)
04-22 09:11:16.175 23178 23178 E AndroidRuntime:        at android.app.ActivityThread.performLaunchActivity(ActivityThread.java:4692)
04-22 09:11:16.175 23178 23178 E AndroidRuntime:        ... 13 more
04-22 09:17:27.596 29119 29119 E AndroidRuntime: FATAL EXCEPTION: main
04-22 09:17:27.596 29119 29119 E AndroidRuntime: Process: com.swimvpn.app, PID: 29119
04-22 09:17:27.596 29119 29119 E AndroidRuntime: java.lang.RuntimeException: Unable to start activity ComponentInfo{com.swimvpn.app/com.swimvpn.app.MainActivity}: java.lang.IllegalStateException: You need to use a Theme.AppCompat theme (or descendant) with this activity.
04-22 09:17:27.596 29119 29119 E AndroidRuntime:        at android.app.ActivityThread.performLaunchActivity(ActivityThread.java:4710)
04-22 09:17:27.596 29119 29119 E AndroidRuntime:        at android.app.ActivityThread.handleLaunchActivity(ActivityThread.java:4941)
04-22 09:17:27.596 29119 29119 E AndroidRuntime:        at android.app.servertransaction.LaunchActivityItem.execute(LaunchActivityItem.java:222)
04-22 09:17:27.596 29119 29119 E AndroidRuntime:        at android.app.servertransaction.TransactionExecutor.executeNonLifecycleItem(TransactionExecutor.java:133)
04-22 09:17:27.596 29119 29119 E AndroidRuntime:        at android.app.servertransaction.TransactionExecutor.executeTransactionItems(TransactionExecutor.java:103)
04-22 09:17:27.596 29119 29119 E AndroidRuntime:        at android.app.servertransaction.TransactionExecutor.execute(TransactionExecutor.java:80)
04-22 09:17:27.596 29119 29119 E AndroidRuntime:        at android.app.ActivityThread$H.handleMessage(ActivityThread.java:3150)
04-22 09:17:27.596 29119 29119 E AndroidRuntime:        at android.os.Handler.dispatchMessage(Handler.java:110)
04-22 09:17:27.596 29119 29119 E AndroidRuntime:        at android.os.Looper.loopOnce(Looper.java:273)
04-22 09:17:27.596 29119 29119 E AndroidRuntime:        at android.os.Looper.loop(Looper.java:363)
04-22 09:17:27.596 29119 29119 E AndroidRuntime:        at android.app.ActivityThread.main(ActivityThread.java:10060)
04-22 09:17:27.596 29119 29119 E AndroidRuntime:        at java.lang.reflect.Method.invoke(Native Method)
04-22 09:17:27.596 29119 29119 E AndroidRuntime:        at com.android.internal.os.RuntimeInit$MethodAndArgsCaller.run(RuntimeInit.java:632)
04-22 09:17:27.596 29119 29119 E AndroidRuntime:        at com.android.internal.os.ZygoteInit.main(ZygoteInit.java:975)
04-22 09:17:27.596 29119 29119 E AndroidRuntime: Caused by: java.lang.IllegalStateException: You need to use a Theme.AppCompat theme (or descendant) with this activity.
04-22 09:17:27.596 29119 29119 E AndroidRuntime:        at androidx.appcompat.app.AppCompatDelegateImpl.createSubDecor(AppCompatDelegateImpl.java:902)
04-22 09:17:27.596 29119 29119 E AndroidRuntime:        at androidx.appcompat.app.AppCompatDelegateImpl.ensureSubDecor(AppCompatDelegateImpl.java:865)
04-22 09:17:27.596 29119 29119 E AndroidRuntime:        at androidx.appcompat.app.AppCompatDelegateImpl.setContentView(AppCompatDelegateImpl.java:757)
04-22 09:17:27.596 29119 29119 E AndroidRuntime:        at androidx.appcompat.app.AppCompatActivity.setContentView(AppCompatActivity.java:209)
04-22 09:17:27.596 29119 29119 E AndroidRuntime:        at androidx.activity.compose.ComponentActivityKt.setContent(ComponentActivity.kt:70)
04-22 09:17:27.596 29119 29119 E AndroidRuntime:        at androidx.activity.compose.ComponentActivityKt.setContent$default(ComponentActivity.kt:51)
04-22 09:17:27.596 29119 29119 E AndroidRuntime:        at com.swimvpn.app.MainActivity.onCreate(MainActivity.kt:92)
04-22 09:17:27.596 29119 29119 E AndroidRuntime:        at android.app.Activity.performCreate(Activity.java:9519)
04-22 09:17:27.596 29119 29119 E AndroidRuntime:        at android.app.Activity.performCreate(Activity.java:9488)
04-22 09:17:27.596 29119 29119 E AndroidRuntime:        at android.app.Instrumentation.callActivityOnCreate(Instrumentation.java:1524)
04-22 09:17:27.596 29119 29119 E AndroidRuntime:        at android.app.ActivityThread.performLaunchActivity(ActivityThread.java:4692)
04-22 09:17:27.596 29119 29119 E AndroidRuntime:        ... 13 more
04-22 15:26:18.969 23241 23241 E AndroidRuntime: FATAL EXCEPTION: main
04-22 15:26:18.969 23241 23241 E AndroidRuntime: Process: com.swimvpn.app, PID: 23241
04-22 15:26:18.969 23241 23241 E AndroidRuntime: java.lang.RuntimeException: Unable to start activity ComponentInfo{com.swimvpn.app/com.swimvpn.app.MainActivity}: java.lang.IllegalStateException: You need to use a Theme.AppCompat theme (or descendant) with this activity.
04-22 15:26:18.969 23241 23241 E AndroidRuntime:        at android.app.ActivityThread.performLaunchActivity(ActivityThread.java:4710)
04-22 15:26:18.969 23241 23241 E AndroidRuntime:        at android.app.ActivityThread.handleLaunchActivity(ActivityThread.java:4941)
04-22 15:26:18.969 23241 23241 E AndroidRuntime:        at android.app.servertransaction.LaunchActivityItem.execute(LaunchActivityItem.java:222)
04-22 15:26:18.969 23241 23241 E AndroidRuntime:        at android.app.servertransaction.TransactionExecutor.executeNonLifecycleItem(TransactionExecutor.java:133)
04-22 15:26:18.969 23241 23241 E AndroidRuntime:        at android.app.servertransaction.TransactionExecutor.executeTransactionItems(TransactionExecutor.java:103)
04-22 15:26:18.969 23241 23241 E AndroidRuntime:        at android.app.servertransaction.TransactionExecutor.execute(TransactionExecutor.java:80)
04-22 15:26:18.969 23241 23241 E AndroidRuntime:        at android.app.ActivityThread$H.handleMessage(ActivityThread.java:3150)
04-22 15:26:18.969 23241 23241 E AndroidRuntime:        at android.os.Handler.dispatchMessage(Handler.java:110)
04-22 15:26:18.969 23241 23241 E AndroidRuntime:        at android.os.Looper.loopOnce(Looper.java:273)
04-22 15:26:18.969 23241 23241 E AndroidRuntime:        at android.os.Looper.loop(Looper.java:363)
04-22 15:26:18.969 23241 23241 E AndroidRuntime:        at android.app.ActivityThread.main(ActivityThread.java:10060)
04-22 15:26:18.969 23241 23241 E AndroidRuntime:        at java.lang.reflect.Method.invoke(Native Method)
04-22 15:26:18.969 23241 23241 E AndroidRuntime:        at com.android.internal.os.RuntimeInit$MethodAndArgsCaller.run(RuntimeInit.java:632)
04-22 15:26:18.969 23241 23241 E AndroidRuntime:        at com.android.internal.os.ZygoteInit.main(ZygoteInit.java:975)
04-22 15:26:18.969 23241 23241 E AndroidRuntime: Caused by: java.lang.IllegalStateException: You need to use a Theme.AppCompat theme (or descendant) with this activity.
04-22 15:26:18.969 23241 23241 E AndroidRuntime:        at androidx.appcompat.app.AppCompatDelegateImpl.createSubDecor(AppCompatDelegateImpl.java:902)
04-22 15:26:18.969 23241 23241 E AndroidRuntime:        at androidx.appcompat.app.AppCompatDelegateImpl.ensureSubDecor(AppCompatDelegateImpl.java:865)
04-22 15:26:18.969 23241 23241 E AndroidRuntime:        at androidx.appcompat.app.AppCompatDelegateImpl.setContentView(AppCompatDelegateImpl.java:757)
04-22 15:26:18.969 23241 23241 E AndroidRuntime:        at androidx.appcompat.app.AppCompatActivity.setContentView(AppCompatActivity.java:209)
04-22 15:26:18.969 23241 23241 E AndroidRuntime:        at androidx.activity.compose.ComponentActivityKt.setContent(ComponentActivity.kt:70)
04-22 15:26:18.969 23241 23241 E AndroidRuntime:        at androidx.activity.compose.ComponentActivityKt.setContent$default(ComponentActivity.kt:51)
04-22 15:26:18.969 23241 23241 E AndroidRuntime:        at com.swimvpn.app.MainActivity.onCreate(MainActivity.kt:92)
04-22 15:26:18.969 23241 23241 E AndroidRuntime:        at android.app.Activity.performCreate(Activity.java:9519)
04-22 15:26:18.969 23241 23241 E AndroidRuntime:        at android.app.Activity.performCreate(Activity.java:9488)
04-22 15:26:18.969 23241 23241 E AndroidRuntime:        at android.app.Instrumentation.callActivityOnCreate(Instrumentation.java:1524)
04-22 15:26:18.969 23241 23241 E AndroidRuntime:        at android.app.ActivityThread.performLaunchActivity(ActivityThread.java:4692)
04-22 15:26:18.969 23241 23241 E AndroidRuntime:        ... 13 more
04-22 15:27:16.636 25515 25515 E AndroidRuntime: FATAL EXCEPTION: main
04-22 15:27:16.636 25515 25515 E AndroidRuntime: Process: com.swimvpn.app, PID: 25515
04-22 15:27:16.636 25515 25515 E AndroidRuntime: java.lang.RuntimeException: Unable to start activity ComponentInfo{com.swimvpn.app/com.swimvpn.app.MainActivity}: java.lang.IllegalStateException: You need to use a Theme.AppCompat theme (or descendant) with this activity.
04-22 15:27:16.636 25515 25515 E AndroidRuntime:        at android.app.ActivityThread.performLaunchActivity(ActivityThread.java:4710)
04-22 15:27:16.636 25515 25515 E AndroidRuntime:        at android.app.ActivityThread.handleLaunchActivity(ActivityThread.java:4941)
04-22 15:27:16.636 25515 25515 E AndroidRuntime:        at android.app.servertransaction.LaunchActivityItem.execute(LaunchActivityItem.java:222)
04-22 15:27:16.636 25515 25515 E AndroidRuntime:        at android.app.servertransaction.TransactionExecutor.executeNonLifecycleItem(TransactionExecutor.java:133)
04-22 15:27:16.636 25515 25515 E AndroidRuntime:        at android.app.servertransaction.TransactionExecutor.executeTransactionItems(TransactionExecutor.java:103)
04-22 15:27:16.636 25515 25515 E AndroidRuntime:        at android.app.servertransaction.TransactionExecutor.execute(TransactionExecutor.java:80)
04-22 15:27:16.636 25515 25515 E AndroidRuntime:        at android.app.ActivityThread$H.handleMessage(ActivityThread.java:3150)
04-22 15:27:16.636 25515 25515 E AndroidRuntime:        at android.os.Handler.dispatchMessage(Handler.java:110)
04-22 15:27:16.636 25515 25515 E AndroidRuntime:        at android.os.Looper.loopOnce(Looper.java:273)
04-22 15:27:16.636 25515 25515 E AndroidRuntime:        at android.os.Looper.loop(Looper.java:363)
04-22 15:27:16.636 25515 25515 E AndroidRuntime:        at android.app.ActivityThread.main(ActivityThread.java:10060)
04-22 15:27:16.636 25515 25515 E AndroidRuntime:        at java.lang.reflect.Method.invoke(Native Method)
04-22 15:27:16.636 25515 25515 E AndroidRuntime:        at com.android.internal.os.RuntimeInit$MethodAndArgsCaller.run(RuntimeInit.java:632)
04-22 15:27:16.636 25515 25515 E AndroidRuntime:        at com.android.internal.os.ZygoteInit.main(ZygoteInit.java:975)
04-22 15:27:16.636 25515 25515 E AndroidRuntime: Caused by: java.lang.IllegalStateException: You need to use a Theme.AppCompat theme (or descendant) with this activity.
04-22 15:27:16.636 25515 25515 E AndroidRuntime:        at androidx.appcompat.app.AppCompatDelegateImpl.createSubDecor(AppCompatDelegateImpl.java:902)
04-22 15:27:16.636 25515 25515 E AndroidRuntime:        at androidx.appcompat.app.AppCompatDelegateImpl.ensureSubDecor(AppCompatDelegateImpl.java:865)
04-22 15:27:16.636 25515 25515 E AndroidRuntime:        at androidx.appcompat.app.AppCompatDelegateImpl.setContentView(AppCompatDelegateImpl.java:757)
04-22 15:27:16.636 25515 25515 E AndroidRuntime:        at androidx.appcompat.app.AppCompatActivity.setContentView(AppCompatActivity.java:209)
04-22 15:27:16.636 25515 25515 E AndroidRuntime:        at androidx.activity.compose.ComponentActivityKt.setContent(ComponentActivity.kt:70)
04-22 15:27:16.636 25515 25515 E AndroidRuntime:        at androidx.activity.compose.ComponentActivityKt.setContent$default(ComponentActivity.kt:51)
04-22 15:27:16.636 25515 25515 E AndroidRuntime:        at com.swimvpn.app.MainActivity.onCreate(MainActivity.kt:92)
04-22 15:27:16.636 25515 25515 E AndroidRuntime:        at android.app.Activity.performCreate(Activity.java:9519)
04-22 15:27:16.636 25515 25515 E AndroidRuntime:        at android.app.Activity.performCreate(Activity.java:9488)
04-22 15:27:16.636 25515 25515 E AndroidRuntime:        at android.app.Instrumentation.callActivityOnCreate(Instrumentation.java:1524)
04-22 15:27:16.636 25515 25515 E AndroidRuntime:        at android.app.ActivityThread.performLaunchActivity(ActivityThread.java:4692)
04-22 15:27:16.636 25515 25515 E AndroidRuntime:        ... 13 more
