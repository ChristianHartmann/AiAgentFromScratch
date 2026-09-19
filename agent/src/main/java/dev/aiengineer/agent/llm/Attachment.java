package dev.aiengineer.agent.llm;

import java.util.Arrays;
import java.util.Objects;

/**
 * A file sent along with a user message, such as an image, audio or a PDF that the model
 * reads itself. Compared by content; a record would compare the arrays by identity.
 */
public record Attachment(byte[] data, String mimeType) {

	public Attachment {
		Objects.requireNonNull(data, "data");
		Objects.requireNonNull(mimeType, "mimeType");
		data = data.clone();
	}

	@Override
	public byte[] data() {
		return data.clone();
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof Attachment attachment && mimeType.equals(attachment.mimeType)
				&& Arrays.equals(data, attachment.data);
	}

	@Override
	public int hashCode() {
		return 31 * mimeType.hashCode() + Arrays.hashCode(data);
	}

	@Override
	public String toString() {
		return "Attachment[" + mimeType + ", " + data.length + " bytes]";
	}
}
