package com.mohammadag.unlockeffectbeforesecuritycode;

import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static de.robv.android.xposed.XposedHelpers.getStaticBooleanField;
import static de.robv.android.xposed.XposedHelpers.getStaticObjectField;
import android.content.Context;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.util.Log;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class UnlockEffect implements IXposedHookLoadPackage {

	@Override
	public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
		if (lpparam.packageName.equals("com.android.settings")) {
			//hookSettingsPackage(lpparam);
			return;
		}
		
		String packageName, javaPkg, keyguardUpdateMonitorName, securityModeEnumName, keyguardEffectViewName;
		String backgroundName, foregroundName;
		
		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
			packageName = "com.android.keyguard";
			javaPkg = packageName;
			keyguardEffectViewName = javaPkg + ".sec.KeyguardEffectViewMain";
		} else {
			packageName = "android";
			javaPkg = "com.android.internal.policy.impl.keyguard";
			keyguardEffectViewName = javaPkg + "sec.KeyguardEffectView";
		}
		
		keyguardUpdateMonitorName = javaPkg + ".KeyguardUpdateMonitor";
		securityModeEnumName = javaPkg + ".KeyguardSecurityModel$SecurityMode";
		backgroundName = keyguardEffectViewName + "$Background";
		foregroundName = keyguardEffectViewName + "$Foreground";
		
		if (!lpparam.packageName.equals(packageName))
			return;
		
		final Class<?> keyguardUpdateMonitor = findClass(keyguardUpdateMonitorName, lpparam.classLoader);
		final Class<?> securityModeEnum = findClass(securityModeEnumName, lpparam.classLoader);
		final Class<?> keyguardEffectViewClass = findClass(keyguardEffectViewName, lpparam.classLoader);
		//final Class<?> rippleUnlockView = findClass("com.android.internal.policy.impl.keyguard.sec.RippleUnlockView", lpparam.classLoader);
		final Class<?> backgroundEnum = findClass(backgroundName, lpparam.classLoader);
		final Class<?> foregroundEnum = findClass(foregroundName, lpparam.classLoader);
		final Class<?> KeyguardHostView = findClass(javaPkg + ".KeyguardHostView", lpparam.classLoader);
		
		XposedHelpers.findAndHookMethod(KeyguardHostView, "showPrimarySecurityScreen", boolean.class, new XC_MethodHook() {
			@Override
			protected void beforeHookedMethod(MethodHookParam param) {
				boolean turningOff = (Boolean) param.args[0];
				Object mSecurityModel = getObjectField(param.thisObject, "mSecurityModel");	
		        Object securityMode = XposedHelpers.callMethod(mSecurityModel, "getSecurityMode");
		        Object mContext = getObjectField(param.thisObject, "mContext");
		        
		        Object keyguardUpdateMonitorInstance = XposedHelpers.callStaticMethod(keyguardUpdateMonitor, "getInstance", mContext);
		        boolean isAlternateUnlockEnabled = (Boolean) XposedHelpers.callMethod(keyguardUpdateMonitorInstance, "isAlternateUnlockEnabled");
		        
		        if (getStaticBooleanField(KeyguardHostView, "DEBUG"))
		        	Log.v("KeyguardHostView", "showPrimarySecurityScreen(turningOff=" + turningOff + ")");
		        if (turningOff || isAlternateUnlockEnabled) {
		            // If we're not turning off, then allow biometric alternate.
		            // We'll reload it when the device comes back on.
		            securityMode = XposedHelpers.callMethod(mSecurityModel, "getAlternateFor", securityMode);
		            securityMode = getStaticObjectField(securityModeEnum, "None"); 
		        }
		        XposedHelpers.callMethod(param.thisObject, "showSecurityScreen", securityMode);
		        param.setResult(false);
			}
		});
		
		XposedHelpers.findAndHookMethod(keyguardEffectViewClass, "show", new XC_MethodHook() {
			@Override
			protected void beforeHookedMethod(MethodHookParam param) {
				Context mContext = (Context) getObjectField(param.thisObject, "mContext");
				int effect;
				try {
					effect = Settings.System.getInt(mContext.getContentResolver(), "lockscreen_ripple_effect");
				} catch (SettingNotFoundException e) {
					effect = 2;
				}
				
				// 0 = circle, 1 = ripple, 2 = light
				if (effect == 0) {
					XposedHelpers.callMethod(param.thisObject, "makeForeground", getStaticObjectField(foregroundEnum, "circle"));
					Object mForegroundView = getObjectField(param.thisObject, "mForegroundView");
					XposedHelpers.callMethod(mForegroundView, "show");
					XposedHelpers.callMethod(param.thisObject, "makeBackground", getStaticObjectField(backgroundEnum, "wallpaper"));
				} else if (effect == 1) {
					XposedHelpers.callMethod(param.thisObject, "makeForeground", getStaticObjectField(foregroundEnum, "none"));
					Object mBackgroundView = getObjectField(param.thisObject, "mBackgroundView");
					XposedHelpers.callMethod(param.thisObject, "makeBackground", getStaticObjectField(backgroundEnum, "ripple"));
					XposedHelpers.callMethod(mBackgroundView, "show");
				} else if (effect == 2) {
					XposedHelpers.callMethod(param.thisObject, "makeForeground", getStaticObjectField(foregroundEnum, "lens"));
					Object mForegroundView = getObjectField(param.thisObject, "mForegroundView");
					XposedHelpers.callMethod(mForegroundView, "show");
					XposedHelpers.callMethod(param.thisObject, "makeBackground", getStaticObjectField(backgroundEnum, "wallpaper"));
				}
				
				param.setResult(false);
			}
		});
	}
/*
	private void hookSettingsPackage(LoadPackageParam lpparam) throws NoSuchMethodException {	
		final Class<?> lockscreenMenuSettings = findClass("com.android.settings.LockscreenMenuSettings", lpparam.classLoader);
		final Method createPreferenceHierarchy = XposedHelpers.findMethodBestMatch(lockscreenMenuSettings, "createPreferenceHierarchy");

		XposedHelpers.findAndHookMethod(lockscreenMenuSettings, "createPreferenceHierarchy", new XC_MethodReplacement() {
			
			@Override
			protected Object replaceHookedMethod(MethodHookParam param)
					throws Throwable {
				
				PreferenceScreen preferenceScreen = (PreferenceScreen) XposedBridge.invokeOriginalMethod(createPreferenceHierarchy,
						param.thisObject, null);
				Object lockPatternUtils = getObjectField(param.thisObject, "mLockPatternUtils");
				boolean isSecure = (Boolean) XposedHelpers.callMethod(lockPatternUtils, "isSecure");
				
				// Avoid duplicate crap
				if (!isSecure)
					return preferenceScreen;
				
				Method getActivity = null;
				Activity activity = null;
				try {
					getActivity = lockscreenMenuSettings.getMethod("getActivity");
					activity = (Activity) getActivity.invoke(lockscreenMenuSettings);
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
				if (activity == null)
					return preferenceScreen;
				
				CheckBoxPreference mMultipleLockScreen = new CheckBoxPreference(activity);
		        mMultipleLockScreen.setKey("multiple_lock_screen");
		        mMultipleLockScreen.setTitle(2131300320);
		        mMultipleLockScreen.setSummary(2131300321);
		        XposedHelpers.callMethod(mMultipleLockScreen, "setOnPreferenceChangeListener", param.thisObject);
		        mMultipleLockScreen.setOrder(1);
		        
		        XposedHelpers.setObjectField(param.thisObject, "mMultipleLockScreen", mMultipleLockScreen);
				
		        preferenceScreen.addPreference(mMultipleLockScreen);
		        preferenceScreen.removeAll();
		        
		        return preferenceScreen;
			}
		});
	}
	*/
	
	

}
