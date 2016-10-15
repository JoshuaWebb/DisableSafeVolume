package io.github.joshuawebb.disablesafevolume;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class DisableSafeVolume implements IXposedHookLoadPackage {

	public final static String AUDIO_SERVICE_CLASS_NAME = "com.android.server.audio.AudioService";

	@Override
	public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
		if (!lpparam.packageName.equals("android"))
			return;

		Class<?> audioServiceClass = XposedHelpers.findClass(AUDIO_SERVICE_CLASS_NAME, lpparam.classLoader);
		final int disabled = XposedHelpers.getStaticIntField(audioServiceClass, "SAFE_MEDIA_VOLUME_DISABLED");
		final int pollPeriod = XposedHelpers.getStaticIntField(audioServiceClass, "MUSIC_ACTIVE_POLL_PERIOD_MS");

		// Just before the first poll, disable safe volume. No more polls! ^^
		XposedHelpers.findAndHookMethod(audioServiceClass, "onCheckMusicActive", String.class,
			new XC_MethodHook() {
				@Override
				protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
					XposedHelpers.setObjectField(param.thisObject, "mSafeMediaVolumeState", disabled);
				}
			}
		);

		// Perpetually set the active ms to one cycle,
		// this ensures that there's some playback time already recorded
		// so it won't call `enforceSafeMediaVolume` during start-up.
		XposedHelpers.findAndHookMethod(audioServiceClass, "saveMusicActiveMs",
			new XC_MethodHook() {
				@Override
				protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
					XposedHelpers.setIntField(param.thisObject, "mMusicActiveMs", pollPeriod);
				}
			}
		);

		// This is just in case, for whatever reason, this still gets called.
		// If this does get called log the parameters it was called with.
		XposedHelpers.findAndHookMethod(audioServiceClass, "setSafeMediaVolumeEnabled", "boolean", String.class,
			 new XC_MethodHook() {
				@Override
				protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
					String methodName = param.method.getName();
					String msg = String.format("%s(%s, %s)", methodName, param.args[0], param.args[1]);
					XposedBridge.log(msg);
				}
			}
		);
	}
}
