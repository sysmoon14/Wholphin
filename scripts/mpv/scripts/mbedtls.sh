#!/bin/bash -e

. ../../include/path.sh

if [ "$1" == "build" ]; then
	true
elif [ "$1" == "clean" ]; then
	if [ -f Makefile ]; then
		make clean || true
	fi
	exit 0
else
	exit 255
fi

# Try to clean if Makefile exists, but don't fail if it doesn't
if [ -f Makefile ]; then
	$0 clean || true
fi
if [[ "$ndk_triple" == "i686"* ]]; then
	./scripts/config.py unset MBEDTLS_AESNI_C
else
	./scripts/config.py set MBEDTLS_AESNI_C
fi

make -j$cores no_test
make DESTDIR="$prefix_dir" install
