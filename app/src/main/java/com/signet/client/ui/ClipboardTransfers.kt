package com.signet.client.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle

object ClipboardTransfers {
    fun read(context:Context,max:Int):String {
        val clip=context.getSystemService(ClipboardManager::class.java).primaryClip?:error("Буфер обмена пуст")
        require(clip.itemCount>0)
        val text=clip.getItemAt(0).text?.toString()?:error("В буфере нет текста")
        require(text.toByteArray().size<=max){"Содержимое буфера слишком большое"}
        return text.trim()
    }
    fun copy(context:Context,label:String,text:String){
        val clip=ClipData.newPlainText(label,text)
        clip.description.extras=PersistableBundle().apply{putBoolean("android.content.extra.IS_SENSITIVE",true)}
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
    }
}
