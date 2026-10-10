package pl.modlin

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule

typealias SerializableStateFlow<T> =
        @Serializable(with = MutableStateFlowSerializer::class)
        MutableStateFlow<T>

class MutableStateFlowSerializer<T>(
    private val valueSerializer: KSerializer<T>
) : KSerializer<MutableStateFlow<T>> {

    override val descriptor: SerialDescriptor =
        valueSerializer.descriptor

    override fun serialize(
        encoder: Encoder,
        value: MutableStateFlow<T>
    ) {
        encoder.encodeSerializableValue(valueSerializer, value.value)
    }

    override fun deserialize(decoder: Decoder): MutableStateFlow<T> {
        val value = decoder.decodeSerializableValue(valueSerializer)
        return MutableStateFlow(value)
    }
}

val ModlinJson = Json {
    serializersModule = SerializersModule {
        contextual(MutableStateFlow::class) { serializers ->
            @Suppress("UNCHECKED_CAST")
            MutableStateFlowSerializer(
                serializers[0] as KSerializer<Any?>
            )
        }
    }
}