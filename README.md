dview-flipper-view
![Release](https://jitpack.io/v/dora4/dview-flipper-view.svg)
--------------------------------

#### 运行效果
![通知跑马灯](https://github.com/user-attachments/assets/3cbff2aa-c839-411d-ae73-48959141a5b4)

#### 卡片
![DORA视图 神之卷轴](https://github.com/user-attachments/assets/d60fa64e-be04-49bb-9abf-e2bd7c9e8d0e)

#### 规范标准
此控件遵循《Dora View规范手册》 https://github.com/dora4/dview-template/blob/main/Naming_Convention_Guide.md

#### Gradle依赖配置

```groovy
// 添加以下代码到项目根目录下的build.gradle
allprojects {
    repositories {
        maven { url "https://jitpack.io" }
    }
}
// 添加以下代码到app模块的build.gradle
dependencies {
    implementation 'com.github.dora4:dview-flipper-view:1.9'
}
```

#### 使用方式
activity_flipper_view.xml
```xml
<?xml version="1.0" encoding="utf-8"?>
<layout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    tools:context=".ui.FlipperViewActivity">

    <data>

    </data>

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:background="@color/colorPanelBg"
        android:orientation="vertical">

        <dora.widget.DoraTitleBar
            android:id="@+id/titleBar"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:background="@color/colorPrimary"
            app:dview_title="@string/common_title" />

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:orientation="vertical"
            android:padding="10dp">

            <dora.widget.DoraFlipperView
                android:id="@+id/fv1"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                app:dview_fv_flipInterval="5000"
                app:dview_fv_textColor="@color/white"
                app:dview_fv_textSize="15sp"
                android:background="#ffaaa5"/>
            <dora.widget.DoraFlipperView
                android:id="@+id/fv2"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                app:dview_fv_flipInterval="5000"
                app:dview_fv_textColor="@color/white"
                app:dview_fv_textSize="15sp"
                android:background="#ffd3b6"/>
            <dora.widget.DoraFlipperView
                android:id="@+id/fv3"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                app:dview_fv_flipInterval="5000"
                app:dview_fv_textColor="@color/colorTextNormal"
                app:dview_fv_textSize="15sp"
                android:background="#fdffab"/>
            <dora.widget.DoraFlipperView
                android:id="@+id/fv4"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                app:dview_fv_flipInterval="5000"
                app:dview_fv_textColor="@color/colorTextNormal"
                app:dview_fv_textSize="15sp"
                android:background="#a8e6cf"/>
        </LinearLayout>
    </LinearLayout>

</layout>
```
Kotlin代码。
```kt
binding.fv1.setFlipperListener(object : DoraFlipperView.FlipperListener {

            override fun onFlipFinish() {
            }

            override fun onFlipStart() {
            }

            override fun onLoadText(index: Int, text: String) {
            }

            override fun onItemClick(index: Int, text: String) {
            }
        })
        // 航班 1
        binding.fv1.addText("航班号: CA123")
        binding.fv1.addText("出发地: 北京(PEK)")
        binding.fv1.addText("目的地: 上海(SHA)")
        binding.fv1.addText("起飞时间: 08:30")
        binding.fv1.addText("到达时间: 10:50")

        // 航班 2
        binding.fv2.addText("航班号: MU456")
        binding.fv2.addText("出发地: 广州(CAN)")
        binding.fv2.addText("目的地: 成都(CTU)")
        binding.fv2.addText("起飞时间: 13:15")
        binding.fv2.addText("到达时间: 15:55")

        // 航班 3
        binding.fv3.addText("航班号: CZ789")
        binding.fv3.addText("出发地: 深圳(SZX)")
        binding.fv3.addText("目的地: 西安(XIY)")
        binding.fv3.addText("起飞时间: 17:40")
        binding.fv3.addText("到达时间: 20:20")

        // 航班 4
        binding.fv4.addText("航班号: HU321")
        binding.fv4.addText("出发地: 香港(HKG)")
        binding.fv4.addText("目的地: 北京(PEK)")
        binding.fv4.addText("起飞时间: 21:10")
        binding.fv4.addText("到达时间: 23:55")
```

