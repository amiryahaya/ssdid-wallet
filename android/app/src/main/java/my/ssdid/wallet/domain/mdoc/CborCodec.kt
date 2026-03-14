package my.ssdid.wallet.domain.mdoc

import com.upokecenter.cbor.CBORObject

object CborCodec {

    fun encodeDataElement(value: Any): ByteArray {
        return toCborObject(value).EncodeToBytes()
    }

    fun decodeDataElement(bytes: ByteArray): Any {
        return fromCborObject(CBORObject.DecodeFromBytes(bytes))
    }

    fun encodeMap(map: Map<String, Any>): ByteArray {
        val cbor = CBORObject.NewMap()
        for ((key, value) in map) {
            cbor[key] = toCborObject(value)
        }
        return cbor.EncodeToBytes()
    }

    fun decodeMap(bytes: ByteArray): Map<String, Any> {
        val cbor = CBORObject.DecodeFromBytes(bytes)
        return cborMapToMap(cbor)
    }

    fun toCborObject(value: Any): CBORObject {
        return when (value) {
            is String -> CBORObject.FromObject(value)
            is Int -> CBORObject.FromObject(value)
            is Long -> CBORObject.FromObject(value)
            is Boolean -> CBORObject.FromObject(value)
            is ByteArray -> CBORObject.FromObject(value)
            is Map<*, *> -> {
                val cbor = CBORObject.NewMap()
                @Suppress("UNCHECKED_CAST")
                for ((k, v) in value as Map<String, Any>) {
                    cbor[k] = toCborObject(v)
                }
                cbor
            }
            is List<*> -> {
                val cbor = CBORObject.NewArray()
                for (item in value) {
                    cbor.Add(toCborObject(item ?: ""))
                }
                cbor
            }
            is CBORObject -> value
            else -> CBORObject.FromObject(value.toString())
        }
    }

    fun fromCborObject(cbor: CBORObject): Any {
        return when {
            cbor.isNumber -> if (cbor.CanValueFitInInt32()) cbor.AsInt32Value() else cbor.AsInt64Value()
            cbor.type == com.upokecenter.cbor.CBORType.TextString -> cbor.AsString()
            cbor.type == com.upokecenter.cbor.CBORType.ByteString -> cbor.GetByteString()
            cbor.type == com.upokecenter.cbor.CBORType.Boolean -> cbor.AsBoolean()
            cbor.type == com.upokecenter.cbor.CBORType.Map -> cborMapToMap(cbor)
            cbor.type == com.upokecenter.cbor.CBORType.Array -> cbor.values.map { fromCborObject(it) }
            else -> cbor.toString()
        }
    }

    private fun cborMapToMap(cbor: CBORObject): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        for (key in cbor.keys) {
            val keyStr = key.AsString()
            result[keyStr] = fromCborObject(cbor[key])
        }
        return result
    }
}
