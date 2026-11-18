package com.signet.client.qr

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.*
import com.signet.core.ImportReview
import java.util.concurrent.atomic.AtomicBoolean

/** Non-exported screen: request runtime permission before opening a camera. */
class QrScannerActivity: ComponentActivity() {
    private var granted by mutableStateOf(false)
    private var error by mutableStateOf<String?>(null)
    private var camera: DecoratedBarcodeView?=null
    private val delivered=AtomicBoolean(false)
    private val permission=registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted=it; if(!it) error="Доступ к камере не предоставлен. Разрешите его в настройках приложения или импортируйте QR из изображения."
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);enableEdgeToEdge()
        granted=checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED
        setContent {
            var torch by remember { mutableStateOf(false) }
            MaterialTheme(colorScheme=darkColorScheme(primary=Color(0xFF39C8FF),surface=Color(0xFF10182A))) {
                Box(Modifier.fillMaxSize().background(Color(0xFF050816)).safeDrawingPadding()) {
                    if(granted && packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
                        AndroidView(factory={context->DecoratedBarcodeView(context).apply {
                            setStatusText("");barcodeView.decoderFactory=DefaultDecoderFactory(listOf(BarcodeFormat.QR_CODE))
                            camera=this
                            barcodeView.addStateListener(object:CameraPreview.StateListener {
                                override fun previewSized(){}
                                override fun previewStarted(){}
                                override fun previewStopped(){}
                                override fun cameraClosed(){}
                                override fun cameraError(exception:Exception){error="Камера недоступна. Закройте другое приложение камеры или импортируйте QR из изображения."}
                            })
                            decodeContinuous(object:BarcodeCallback {
                                override fun barcodeResult(result:BarcodeResult) {
                                    if(!delivered.compareAndSet(false,true))return
                                    val text=result.text
                                    if(!ImportReview.supported(text)) {
                                        error="QR не содержит поддерживаемую подписку или Xray-конфигурацию";pause()
                                    } else {pause();setResult(RESULT_OK,Intent().putExtra("content",text));finish()}
                                }
                            });resume()
                        }},modifier=Modifier.fillMaxSize())
                        Box(Modifier.align(Alignment.Center).sizeIn(maxWidth=280.dp,maxHeight=280.dp).fillMaxSize(.72f).border(2.dp,Color(0xFF39C8FF),RoundedCornerShape(24.dp)))
                    } else Text(error?:if(granted)"На устройстве нет доступной камеры. Импортируйте QR из изображения."else"Для сканирования нужен доступ к камере",Modifier.align(Alignment.Center).padding(24.dp),color=Color.White)
                    Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().background(Color(0xDD050816)).padding(16.dp)) {
                        Text("SIGNET · Сканировать QR",color=Color.White,style=MaterialTheme.typography.titleLarge)
                        Text("После распознавания вы увидите данные перед импортом",color=Color(0xFF9BAAC2))
                        error?.let { Text(it,color=Color(0xFFFFC857)) }
                    }
                    Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color(0xDD050816)).padding(16.dp),horizontalArrangement=Arrangement.SpaceEvenly) {
                        TextButton(onClick={finish()}){Text("Закрыть")}
                        if(granted) {
                            if(packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)) TextButton(onClick={torch=!torch;if(torch)camera?.setTorchOn()else camera?.setTorchOff()}){Text(if(torch)"Выключить фонарик"else"Фонарик")}
                            if(error!=null) TextButton(onClick={error=null;delivered.set(false);camera?.resume()}){Text("Повторить")}
                        } else TextButton(onClick={permission.launch(Manifest.permission.CAMERA)}){Text("Разрешить камеру")}
                    }
                }
            }
        }
        if(!granted)permission.launch(Manifest.permission.CAMERA)
    }
    override fun onResume(){super.onResume();if(granted&&!delivered.get())camera?.resume()}
    override fun onPause(){camera?.pause();super.onPause()}
    override fun onDestroy(){camera?.pause();camera=null;super.onDestroy()}
}
