package com.signet.core

import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest

/** Repair interrupted geodata copies, streaming from this APK under a cross-process file lock. */
object VerifiedAssets {
    private fun hash(input:InputStream):ByteArray=input.use{stream->
        val digest=MessageDigest.getInstance("SHA-256");val buffer=ByteArray(8192)
        while(true){val count=stream.read(buffer);if(count<0)break;digest.update(buffer,0,count)}
        digest.digest()
    }
    @Synchronized fun prepare(directory:File,names:List<String>,open:(String)->InputStream):File {
        check(directory.isDirectory||directory.mkdirs())
        FileOutputStream(File(directory,".assets.lock"),true).channel.use{channel->channel.lock().use{
            for(name in names){
                require(name in setOf("geoip.dat","geosite.dat"))
                val expected=hash(open(name));val target=File(directory,name);val pending=File(directory,"$name.pending")
                if(target.isFile&&runCatching{hash(target.inputStream()).contentEquals(expected)}.getOrDefault(false)){pending.delete();continue}
                try {
                    open(name).use{input->FileOutputStream(pending).use{out->input.copyTo(out);out.fd.sync()}}
                    check(hash(pending.inputStream()).contentEquals(expected))
                    java.nio.file.Files.move(pending.toPath(),target.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING,java.nio.file.StandardCopyOption.ATOMIC_MOVE)
                }finally{pending.delete()}
            }
        }}
        return directory
    }
}
