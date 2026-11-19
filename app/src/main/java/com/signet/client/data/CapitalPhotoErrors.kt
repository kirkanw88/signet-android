package com.signet.client.data

import java.io.IOException

enum class CapitalError(val label:String){
    NO_IMAGERY("Фотография этого города пока недоступна"),IMAGE("Не удалось открыть фотографию"),UNKNOWN("Не удалось загрузить фотографию");
    companion object {
        fun from(error:Exception):CapitalError=when(error){
            is CapitalFailure->error.code
            is IOException->NO_IMAGERY
            is IllegalArgumentException->IMAGE
            else->UNKNOWN
        }
    }
}
class CapitalFailure(val code:CapitalError):IOException(code.name)
