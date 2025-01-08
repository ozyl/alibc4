package com.maomengte.alibc4

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Build
import com.alibaba.alibclogin.AlibcLogin
import com.alibaba.alibcprotocol.callback.AlibcLoginCallback
import com.alibaba.alibcprotocol.callback.AlibcTradeCallback
import com.alibaba.alibcprotocol.param.AlibcShowParams
import com.alibaba.alibcprotocol.param.AlibcTaokeParams
import com.alibaba.alibcprotocol.param.OpenType
import com.alibaba.baichuan.trade.common.AlibcTradeCommon
import com.baichuan.nb_trade.AlibcTrade
import com.baichuan.nb_trade.callback.AlibcTradeInitCallback
import com.baichuan.nb_trade.core.AlibcTradeBiz
import com.baichuan.nb_trade.core.AlibcTradeSDK
import com.randy.alibcextend.auth.AuthCallback
import com.randy.alibcextend.auth.TopAuth
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.FlutterPlugin.FlutterPluginBinding
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler

/**
 * Alibc4Plugin
 */
class Alibc4Plugin : FlutterPlugin, MethodCallHandler,
    ActivityAware {
    /** The MethodChannel that will the communication between Flutter and native Android
     *
     * This local reference serves to register the plugin with the Flutter Engine and unregister it
     * when the Flutter Engine is detached from the Activity */
    private var channel: MethodChannel? = null
    private var context: Context? = null
    private var activity: Activity? = null

    override fun onAttachedToEngine(flutterPluginBinding: FlutterPluginBinding) {
        context = flutterPluginBinding.applicationContext
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, "alibc4")
        channel!!.setMethodCallHandler(this)
    }


    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "getPlatformVersion" -> result.success("Android " + Build.VERSION.RELEASE)
            "init" -> {
                AlibcTradeCommon.turnOnDebug();
                AlibcTradeCommon.openErrorLog();
                AlibcTradeBiz.turnOnDebug();
                val extParams: MutableMap<String, Any> = HashMap(16)
                extParams["timeout"] = 5000
                AlibcTradeSDK.init(
                    context as Application?,
                    extParams,
                    object : AlibcTradeInitCallback {
                        override fun onSuccess() {
                            result.success(getResult(0))
                        }

                        override fun onFailure(code: Int, msg: String) {
                            result.success(getResult(-1, "$code $msg"))
                        }
                    },
                    AlibcTradeSDK.InitStrategy.INIT
                )
            }
            "login" -> {
                AlibcLogin.getInstance().showLogin(object : AlibcLoginCallback {
                    override fun onSuccess(s: String, openId: String) {
                        LogUtils.e("login>>$openId")
                        result.success(getResult(0, openId))
                    }

                    override fun onFailure(code: Int, msg: String) {
                        // code：错误码  msg： 错误信息
                        result.success(getResult(-1, "$code $msg"))
                    }
                })
            }

            "setISVVersion" -> {
                AlibcTradeCommon.setIsvVersion(call.arguments as String?)
            }

            "logout" -> AlibcLogin.getInstance().logout(object : AlibcLoginCallback {
                override fun onSuccess(s: String, openId: String) {
                    LogUtils.e("logout>>$openId")
                    result.success(getResult(0))
                }

                override fun onFailure(code: Int, msg: String) {
                    // code：错误码  msg： 错误信息
                    result.success(getResult(-1, "$code $msg"))
                }
            })

            "getUserInfo" -> {
                val session = AlibcLogin.getInstance().userInfo
                if (session != null) {
                    result.success(getResult(0, session))
                } else {
                    result.success(getResult(-1))
                }
            }

            "setChannel" -> {
                val map = call.arguments as Map<String, String>
                AlibcTradeSDK.setChannel(
                    map["typeName"],
                    map["channelName"]
                )
                result.success(getResult(0))
            }

            "openByUrl" -> {
                val map = call.arguments as Map<String, String?>
                if (map["url"] != null) {
                    val taokeParams = getTaokeParams(map);
                    AlibcTrade.openByUrl(activity,
                        map["url"],
                        getShowParams(map),
                        taokeParams,
                        taokeParams.extParams,
                        object : AlibcTradeCallback {
                            override fun onSuccess(i: Int, o: Any) {
                                result.success(getResult(0))
                            }

                            override fun onFailure(code: Int, msg: String) {
                                result.success(getResult(-1, "$code $msg"))
                            }
                        })
                } else {
                    result.success(getResult(-1, "url must not be null"))
                }
            }

            "hasLogin" -> {
                result.success(AlibcLogin.getInstance().isLogin)
            }
            "showAuthDialog" -> {
                val map = call.arguments as Map<String, String?>
                showAuthDialog(map["key"] ?: "", map["logo"], map["name"],
                    object : AuthCallback {
                        override fun onSuccess(accessToken: String, expireTime: String) {
                            result.success(
                                getResult(
                                    0, mapOf(
                                        "accessToken" to accessToken,
                                        "expireTime" to expireTime,
                                    )
                                )
                            )
                        }

                        override fun onError(code: String, msg: String) {
                            result.success(getResult(-1, "$code $msg"))
                        }
                    })
            }

            else -> result.notImplemented()
        }
    }

    private fun showAuthDialog(
        appKey: String,
        logo: String?,
        appName: String?,
        callback: AuthCallback
    ) {
        if(logo==null){
            TopAuth.showAuthDialog(
                activity,
                R.mipmap.ic_launcher,
                appName,
                appKey, callback
            )
        }else{
            TopAuth.showAuthDialog(
                activity,
                logo,
                appName,
                appKey, callback
            )
        }
    }

    private fun getTaokeParams(map: Map<*, *>): AlibcTaokeParams {
        LogUtils.e("initParams>>$map")
        // taokeParams（淘客）参数配置：配置aid或pid的方式分佣
        //（注：1、如果走adzoneId的方式分佣打点，需要在extraParams中显式传入taokeAppkey，否则打点失败；
        // 2、如果是打开店铺页面(shop)，需要在extraParams中显式传入sellerId，否则同步打点转链失败）
        val taokeParams = AlibcTaokeParams("", "", "")
        if (map["pid"] != null) {
            taokeParams.pid = map["pid"].toString()
        }
        if (map["subPid"] != null) {
            taokeParams.subPid = (map["subPid"].toString())
        }
        if (map["unionId"] != null) {
            taokeParams.unionId = (map["unionId"].toString())
        }
        taokeParams.extParams = HashMap()
        if (map["sellerId"] != null) {
            taokeParams.extParams["sellerId"] = map["sellerId"].toString()
        }
        if (map["taokeAppkey"] != null) {
            taokeParams.extParams["taokeAppkey"] = map["taokeAppkey"].toString()
        }
        return taokeParams
    }

    private fun getShowParams(map: Map<*, *>): AlibcShowParams {
        val showParams = AlibcShowParams()
        //OpenType（页面打开方式）： 枚举值（Auto和Native），Native表示唤端，Auto表示不做设置
        if (map["openType"] != null && map["openType"] == "auto") {
            showParams.openType = OpenType.Auto
        } else {
            showParams.openType = OpenType.Native
        }
        //clientType表示唤端类型：taobao---唤起淘宝客户端；tmall---唤起天猫客户端
        if (map["clientType"] != null && map["clientType"] == "tmall") {
            showParams.clientType = "tmall"
        } else {
            showParams.clientType = "taobao"
        }
        showParams.openType = OpenType.Native
        showParams.clientType = "taobao"
        if (map["title"] != null) {
            showParams.title = map["title"].toString()
        }
        if (map["degradeUrl"] != null) {
            showParams.degradeUrl = map["degradeUrl"].toString()
        }
        //BACK_URL（小把手）：唤端返回的scheme,如果不传默认将不展示小把手；如果想展示小把手，可以自己传入自定义的scheme
        if (map["backUrl"] != null) {
            showParams.backUrl = map["backUrl"].toString()
        }
        return showParams;
    }

    override fun onDetachedFromEngine(binding: FlutterPluginBinding) {
        channel!!.setMethodCallHandler(null)
    }

    fun getResult(code: Int): Map<String, Any> {
        val map: MutableMap<String, Any> = HashMap()
        map["code"] = code
        return map
    }

    fun getResult(code: Int, msg: Any): Map<String, Any> {
        val map: MutableMap<String, Any> = HashMap()
        map["code"] = code
        map["msg"] = msg
        return map
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activity = binding.activity
    }

    override fun onDetachedFromActivityForConfigChanges() {
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
    }

    override fun onDetachedFromActivity() {
    }
}
