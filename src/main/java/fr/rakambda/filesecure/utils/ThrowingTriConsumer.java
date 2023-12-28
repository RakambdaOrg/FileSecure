package fr.rakambda.filesecure.utils;

import java.io.IOException;

public interface ThrowingTriConsumer<T, U, V>{
	void accept(T t, U u, V v) throws IOException;
}
