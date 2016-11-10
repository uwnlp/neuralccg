package edu.uw.neuralccg.util;

import com.google.protobuf.ByteString;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import edu.uw.neuralccg.SerializationProto.Serialized;

public class SerializationUtil {
	private SerializationUtil() {
	}

	@SuppressWarnings("unchecked")
	public static <T extends Serializable> T deserialize(Serialized proto) {
		try {
			return (T) new ObjectInputStream(
					new ByteArrayInputStream(proto.getValue().toByteArray()))
							.readObject();
		} catch (ClassNotFoundException | IOException e) {
			throw new RuntimeException(e);
		}
	}

	public static <T extends Serializable> Serialized serialize(T serializable) {
		try {
			final ByteArrayOutputStream baos = new ByteArrayOutputStream();
			final ObjectOutputStream oos = new ObjectOutputStream(baos);
			oos.writeObject(serializable);
			oos.flush();
			return Serialized.newBuilder()
					.setValue(ByteString.copyFrom(baos.toByteArray())).build();
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}
	}
}
