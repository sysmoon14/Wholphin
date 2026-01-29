#!/bin/bash -e

. ../../include/path.sh

if [ "$1" == "build" ]; then
	true
elif [ "$1" == "clean" ]; then
	# mbedtls 3.x Makefile may not have a working 'clean' in all contexts; never fail
	if [ -f Makefile ]; then
		(make clean) 2>/dev/null || true
	fi
	exit 0
else
	exit 255
fi

# Try to clean if Makefile exists; never fail (e.g. if 'clean' target is missing)
if [ -f Makefile ]; then
	(make clean) 2>/dev/null || true
fi
if [[ "$ndk_triple" == "i686"* ]]; then
	./scripts/config.py unset MBEDTLS_AESNI_C
else
	./scripts/config.py set MBEDTLS_AESNI_C
fi

make -j$cores no_test
make DESTDIR="$prefix_dir" install
