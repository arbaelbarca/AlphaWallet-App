package com.alphawallet.app.ui.onboarding

import android.content.Intent
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.viewbinding.library.activity.viewBinding
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import com.alphawallet.app.R
import com.alphawallet.app.databinding.ActivityOnBoardingBinding
import com.alphawallet.app.ui.HomeActivity
import com.alphawallet.app.ui.SplashActivity

class OnBoardingActivity : AppCompatActivity() {

    private val layouts = listOf(
        R.layout.layout_onboarding_first,
        R.layout.layout_onboarding_second,
        R.layout.layout_onboarding_third,
    )

    private val adapter = CarouselViewPagerAdapter()

    val binding: ActivityOnBoardingBinding by viewBinding()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_on_boarding)

        initial()
    }

    private fun initial() {
        initData()
    }

    private fun initData() {
        val dotsIndicator = binding.dotsIndicator
        val viewPager = binding.viewPagerOnboard
        viewPager.adapter = adapter
        dotsIndicator.setViewPager(viewPager)

        viewPager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrolled(
                position: Int,
                positionOffset: Float,
                positionOffsetPixels: Int
            ) {
//                if (position == 2) {
//                    binding.btnNext.text = String.format("%s", "Get Started")
//                } else {
//                    binding.btnNext.text = String.format("%s", "Next")
//                }
            }

            override fun onPageSelected(position: Int) {

            }

            override fun onPageScrollStateChanged(state: Int) {

            }

        })

        binding.tvGetStartOnBoard.setOnClickListener {
            startActivity(Intent(this, SplashActivity::class.java))
        }
    }


    inner class CarouselViewPagerAdapter : PagerAdapter() {
        private var layoutInflater: LayoutInflater? = null
        override fun instantiateItem(container: ViewGroup, position: Int): Any {
            layoutInflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
            val view = layoutInflater!!.inflate(layouts[position], container, false)
            container.addView(view)
            when (position) {
                0 -> {
                    val tvSubTitle1 = view.findViewById<TextView>(R.id.tvSubTitleBoard1)
                    val shaderColor = LinearGradient(
                        0f, 0f, 0f, tvSubTitle1.lineHeight.toFloat(), Color.parseColor("#8AD4EC"),
                        Color.parseColor("#FF56A9"), Shader.TileMode.REPEAT
                    )
                    tvSubTitle1.paint.shader = shaderColor
                }
                1 -> {
                    val tvSubTitle2 = view.findViewById<TextView>(R.id.tvSubTitleBoard2)
                    val shaderColor = LinearGradient(
                        0f, 0f, 0f, tvSubTitle2.lineHeight.toFloat(), Color.parseColor("#8AD4EC"),
                        Color.parseColor("#FF56A9"), Shader.TileMode.CLAMP
                    )
                    tvSubTitle2.paint.shader = shaderColor
                }
                else -> {
                    val tvTitle3 = view.findViewById<TextView>(R.id.tvTitleBoard3)
                    val shaderColor = LinearGradient(
                        0f, 0f, 0f, tvTitle3.lineHeight.toFloat(), Color.parseColor("#8AD4EC"),
                        Color.parseColor("#FF56A9"), Shader.TileMode.MIRROR
                    )
                    tvTitle3.paint.shader = shaderColor
                }
            }

            return view
        }

        override fun getCount(): Int {
            return layouts.size
        }

        override fun isViewFromObject(view: View, obj: Any): Boolean {
            return view === obj
        }

        override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
            val view = `object` as View
            container.removeView(view)
        }
    }
}
