package com.picfix.tools.view.activity

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.appsflyer.AFInAppEventParameterName
import com.appsflyer.AFInAppEventType
import com.appsflyer.AppsFlyerLib
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.PlayerView
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase
import com.picfix.tools.R
import com.picfix.tools.adapter.DataAdapter
import com.picfix.tools.bean.FileBean
import com.picfix.tools.bean.MenuResource
import com.picfix.tools.bean.Price
import com.picfix.tools.bean.UserInfo
import com.picfix.tools.callback.Callback
import com.picfix.tools.callback.DialogCallback
import com.picfix.tools.callback.PayCallback
import com.picfix.tools.config.Constant
import com.picfix.tools.controller.LogReportManager
import com.picfix.tools.controller.MediaPlayer
import com.picfix.tools.controller.PayManager
import com.picfix.tools.utils.AppUtil
import com.picfix.tools.utils.JLog
import com.picfix.tools.utils.ToastUtil
import com.picfix.tools.view.base.BaseActivity
import com.picfix.tools.view.views.PaySuccessDialog
import com.picfix.tools.view.views.QuitDialog
import com.picfix.tools.view.views.TermsPop
import com.tencent.mmkv.MMKV
import kotlinx.android.synthetic.main.heart_small.view.*
import kotlinx.android.synthetic.main.item_doc.view.*
import kotlinx.coroutines.*
import java.util.*

class PayActivity : BaseActivity() {
    private lateinit var back: ImageView
    private lateinit var pay: Button

    private lateinit var menuBox: RecyclerView
    private lateinit var playerView: PlayerView
    private lateinit var player: ExoPlayer
    private lateinit var bottomView: FrameLayout
    private lateinit var playBtn: ImageView
    private lateinit var terms: TextView

    private var currentProductId = ""
    private var currentServiceId = 0
    private var currentProductType = "acknowledge"
    private var currentPrice = 3.99f

    private var lastClickTime: Long = 0L
    private var isShow = false

    private var orderSn = ""
    private var uri = "file:///android_asset/export.mp4"

    private lateinit var mAdapter: DataAdapter<MenuResource>
    private var currentPos = 0

    override fun setLayout(): Int {
        return R.layout.a_fix_pay
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun initView() {
        back = findViewById(R.id.iv_back)
        pay = findViewById(R.id.do_pay)
        menuBox = findViewById(R.id.price_list)
        playerView = findViewById(R.id.player)
        bottomView = findViewById(R.id.bottom_view)
        playBtn = findViewById(R.id.play)
        terms = findViewById(R.id.terms)

        back.setOnClickListener { onBackPressed() }
        playBtn.setOnClickListener { MediaPlayer.play(uri) }
        pay.setOnClickListener { checkPay(this, "google") }
        terms.setOnClickListener { showPop() }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window.statusBarColor = ContextCompat.getColor(this, R.color.transparent)
        }

        LogReportManager.logReport("支付页", "页面访问", LogReportManager.LogType.OPERATION)
        firebaseAnalytics("visit", "operation")

    }


    override fun initData() {

        loadMenuBox()

        val width = AppUtil.getScreenWidth(this)
        val layout = playerView.layoutParams
        layout.width = width
        layout.height = 640 * width / 544
        playerView.layoutParams = layout

        val bottomParam = bottomView.layoutParams as FrameLayout.LayoutParams
        bottomParam.topMargin = 640 * width / 544 - AppUtil.dp2px(this, 50f)
        bottomView.layoutParams = bottomParam

    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !isShow) {
            player = MediaPlayer.getPlayer(this)
            playerView.player = player

            val listener = object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_IDLE -> {
                            JLog.i("IDLE")
                        }

                        Player.STATE_BUFFERING -> {
                            JLog.i("BUFFRING")
                        }

                        Player.STATE_READY -> {
                            JLog.i("READY")
                        }

                        Player.STATE_ENDED -> {
                            JLog.i("END")
                        }
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    JLog.i("error = ${error.errorCode}")
                    JLog.i("error = ${error.errorCodeName}")
                    JLog.i("error = ${error.message}")
//                MediaPlayer.release()
                }
            }

            player.addListener(listener)
            MediaPlayer.play(uri)
            isShow = true
        }
    }


    @SuppressLint("NotifyDataSetChanged")
    private fun loadMenuBox() {
        val list = arrayListOf<MenuResource>()
        val index = Random().nextInt(2)
        if (index == 0) {
            list.add(
                MenuResource(
                    "$3.99",
                    "",
                    getString(R.string.pay_item_title_1),
                    getString(R.string.pay_item_des_1),
                    "three_days_free_subscription"
                )
            )
        } else {
            list.add(MenuResource("$3.99", "", getString(R.string.pay_item_title_1), getString(R.string.pay_item_des_2), "piceasy_subscription"))
        }

        list.add(
            MenuResource(
                "$11.88",
                "$3.19/Month",
                getString(R.string.pay_item_title_2),
                getString(R.string.pay_item_des_3),
                "piceasy_subscription_season"
            )
        )
        list.add(
            MenuResource(
                "$47.88",
                "$2.79/Month",
                getString(R.string.pay_item_title_3),
                getString(R.string.pay_item_des_4),
                "piceasy_subscription_yearly"
            )
        )
//        list.add(MenuResource("28.99USD", 0, "Yearly", "use one year", "piceasy_one_year"))
//        list.add(MenuResource("48.88USD", 0, "Permanent", "use permanent", "piceasy_permanent"))

        mAdapter = DataAdapter.Builder<MenuResource>()
            .setData(list)
            .setLayoutId(R.layout.item_doc)
            .addBindView { itemView, itemData, position ->
                itemView.title.text = itemData.name
                itemView.price.text = itemData.type
                itemView.des.text = itemData.des
                itemView.per.text = itemData.icon

                if (position == currentPos) {
                    itemView.setBackgroundResource(R.drawable.shape_rectangle_yellow)
                    itemView.point.setImageResource(R.drawable.point_orange)
                    itemView.des.setTextColor(ResourcesCompat.getColor(resources, R.color.color_orange, null))
                    currentProductId = itemData.productId
                } else {
                    itemView.setBackgroundResource(R.drawable.shape_corner_white)
                    itemView.point.setImageResource(R.drawable.point_grey)
                }

                if (position == 0) {
                    itemView.per.visibility = View.GONE
                }

                itemView.setOnClickListener {
                    currentPos = position
                    mAdapter.notifyDataSetChanged()
                }

            }
            .create()

        menuBox.layoutManager = LinearLayoutManager(this)
        menuBox.adapter = mAdapter
        mAdapter.notifyDataSetChanged()
    }

    @SuppressLint("SetTextI18n")
    private fun getServicePrice() {

        PayManager.getInstance().getServiceList(this) {
            val packDetails = arrayListOf<Price>()
            for (child in it) {
                if (child.server_code == Constant.PHOTO_FIX) {
                    packDetails.add(child)
                }

                if (child.server_code == Constant.PHOTO_FIX_TIMES) {
                    packDetails.add(child)
                }
            }

            for (child in packDetails) {
                val menuResource = MenuResource(
                    child.sale_price,
                    child.sale_price,
                    getString(R.string.pay_item_title_1),
                    getString(R.string.pay_item_des_3),
                    child.id.toString()
                )
            }

        }
    }


    private fun checkPay(c: Activity, type: String) {

        if (lastClickTime == 0L) {
            lastClickTime = System.currentTimeMillis()
        } else if (System.currentTimeMillis() - lastClickTime < 2 * 1000) {
            ToastUtil.showShort(c, "Please don't initiate payment frequently")
            return
        }

        lastClickTime = System.currentTimeMillis()

        val userInfo = MMKV.defaultMMKV()?.decodeParcelable("userInfo", UserInfo::class.java)
        if (userInfo != null) {
            val userType = userInfo.user_type
            if (userType == 2) {
                startActivity(Intent(this, LoginActivity::class.java))
                return
            }
        }

        when (currentProductId) {
            "three_days_free_subscription", "piceasy_subscription" -> currentPrice = 3.99f
            "piceasy_subscription_season" -> currentPrice = 11.88f
            "piceasy_subscription_yearly" -> currentPrice = 47.88f
        }

        doPay(c, type)

        LogReportManager.logReport("支付页", "发起支付$($currentPrice)", LogReportManager.LogType.OPERATION)
        firebaseAnalytics("start_pay", "$${currentPrice}")
    }


    private fun doPay(c: Activity, type: String) {
        when (type) {
            "google" -> {
                PayManager.getInstance().doGoogleFastPay(c, currentProductId, currentProductType, object : PayCallback {
                    override fun success() {
                        openPaySuccessDialog()
                        //firebase pay
                        val bundle = Bundle()
                        bundle.putString(FirebaseAnalytics.Param.CURRENCY, "USD")
                        bundle.putFloat(FirebaseAnalytics.Param.VALUE, currentPrice)
                        bundle.putString(FirebaseAnalytics.Param.AFFILIATION, "Google Play")
                        Firebase.analytics.logEvent(FirebaseAnalytics.Event.PURCHASE, bundle)

                        //appsFlyer pay
                        val eventValues = HashMap<String, Any>()
                        eventValues[AFInAppEventParameterName.PURCHASE_CURRENCY] = "USD"
                        eventValues[AFInAppEventParameterName.REVENUE] = currentPrice
                        eventValues[AFInAppEventParameterName.CUSTOMER_USER_ID] = Constant.USER_ID
                        eventValues[AFInAppEventParameterName.CONTENT_ID] = "Google Play"
                        eventValues[AFInAppEventParameterName.CONTENT_TYPE] = "in_app_purchase"
                        AppsFlyerLib.getInstance().logEvent(applicationContext, AFInAppEventType.PURCHASE, eventValues)
                    }

                    override fun progress(orderId: String) {
                        orderSn = orderId
                    }

                    override fun failed(msg: String) {
                        launch(Dispatchers.Main) {
                            ToastUtil.showShort(c, msg)
                            firebaseAnalytics("pay_cancel", "operation")
                        }
                    }
                })
            }

            "alipay" -> {
                PayManager.getInstance().doAliPay(c, currentServiceId, object : PayCallback {
                    override fun success() {
                        launch(Dispatchers.Main) {

                            //支付成功
                            ToastUtil.showShort(c, "支付成功")


                            //根据套餐判断是否跳转到补价页面
//                            if (currentServiceId == secondServiceId) {
//                                toPaySuccessPage()
//                            } else {
//                                openPaySuccessDialog()
//                            }

                        }
                    }

                    override fun progress(orderId: String) {
                        orderSn = orderId
                    }

                    override fun failed(msg: String) {
                        launch(Dispatchers.Main) {
                            ToastUtil.showShort(c, msg)
                        }
                    }
                })
            }
        }

    }


    private fun openPaySuccessDialog() {
        PaySuccessDialog(this@PayActivity, object : DialogCallback {
            override fun onSuccess(file: FileBean) {
                setResult(0x100)
                finish()
            }

            override fun onCancel() {
                setResult(0x100)
                finish()
            }
        }).show()
    }

    private fun showPop() {
        TermsPop(this, object : Callback {
            override fun onSuccess() {
                val intent = Intent(this@PayActivity, AgreementActivity::class.java)
                intent.putExtra("index", 1)
                startActivity(intent)
            }

            override fun onCancel() {
            }
        }).showPopupWindow(pay)
    }


    override fun onBackPressed() {
        QuitDialog(this, getString(R.string.quite_title), object : DialogCallback {
            override fun onSuccess(file: FileBean) {
                LogReportManager.logReport("支付页", "退出页面", LogReportManager.LogType.OPERATION)
                firebaseAnalytics("quit_pay", "operation")
                finish()
            }

            override fun onCancel() {
            }
        }).show()
    }

    override fun onStop() {
        super.onStop()
        MediaPlayer.stop()
    }


    private fun firebaseAnalytics(key: String, value: String) {
        val bundle = Bundle()
        bundle.putString(key, value)
        Firebase.analytics.logEvent("page_payment", bundle)

        val eventValues = HashMap<String, Any>()
        eventValues[AFInAppEventParameterName.CONTENT] = "page_payment"
        if (key == "visit") {
            AppsFlyerLib.getInstance().logEvent(applicationContext, "page_payment", eventValues)
        } else {
            AppsFlyerLib.getInstance().logEvent(applicationContext, key, eventValues)
        }
    }

}