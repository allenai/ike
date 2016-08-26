package org.allenai.ike

import org.allenai.ike.patterns.NamedPattern

import com.esotericsoftware.kryo.io.{ Input, Output }
import com.esotericsoftware.kryo.{ Kryo, Serializer }
import org.apache.spark.serializer.KryoRegistrator

/** Helper to register custom serializers for Some and None. Since Kryo is a Java library, it
  * doesn't provide default serializers for these classes.
  */
object OptionSerializers {
  def register(kryo: Kryo) {
    kryo.register(Class.forName("scala.None$"), new NoneSerializer())
    kryo.register(classOf[Some[_]], new SomeSerializer(kryo))
  }
}

class NoneSerializer extends Serializer[None.type] {
  override def write(kryo: Kryo, output: Output, `object`: None.type): Unit = ()

  override def read(kryo: Kryo, input: Input, `type`: Class[None.type]): None.type = None
}

class SomeSerializer(kryo: Kryo) extends Serializer[Some[_]] {
  override def write(kryo: Kryo, output: Output, `object`: Some[_]): Unit = {
    kryo.writeClassAndObject(output, `object`.get)
  }

  override def read(kryo: Kryo, input: Input, `type`: Class[Some[_]]): Some[_] =
    Some(kryo.readClassAndObject(input))
}

/** Helper class to register custom classes with Kryo. This allows Kryo to pack these objects more
  * efficiently with numbered indices, rather than using the fully qualified class name.
  */
class IkeKryoRegistrator extends KryoRegistrator {
  override def registerClasses(kryo: Kryo): Unit = {
    OptionSerializers.register(kryo)
    kryo.register(Class.forName("scala.collection.immutable.Nil$"))

    val classes: Array[Class[_]] = Array(
      classOf[BlackLabResult],
      classOf[Interval],
      classOf[WordData],
      classOf[java.time.Instant],
      classOf[java.time.LocalDate],
      classOf[java.time.Year]
    )

    classes.foreach(kryo.register)
  }
}
