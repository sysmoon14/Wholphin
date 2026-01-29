#!/bin/bash -e

. ../../include/path.sh

if [ "$1" == "build" ]; then
	true
elif [ "$1" == "clean" ]; then
	make clean
	exit 0
else
	exit 255
fi

# Building seperately from source tree is not supported, this means we are forced to always clean
$0 clean

mycflags=(
	# ensures correct linking into libmpv.so
	-fPIC
	# bionic is missing decimal_point in localeconv [src/llex.c]
	-Dgetlocaledecpoint\\\(\\\)=\\\(46\\\)
	# force fallback as ftello/fseeko are not defined [src/liolib.c]
	-Dlua_fseek
)

# LUA_T= and LUAC_T= to disable building lua & luac
# -Dgetlocaledecpoint()=('.') fixes bionic missing decimal_point in localeconv
make CC="$CC" AR="$AR rc" RANLIB="$RANLIB" \
	MYCFLAGS="${mycflags[*]}" \
	PLAT=linux LUA_T= LUAC_T= -j$cores

# TO_BIN=/dev/null skips installing lua & luac (avoids "install: missing destination" when TO_BIN= is parsed wrong)
# Lua 5.2 Makefile install target then removes installed files (lines 66-69); reinstall manually
make INSTALL=${INSTALL:-install} INSTALL_TOP="$prefix_dir" TO_BIN=/dev/null install
mkdir -p "$prefix_dir/include" "$prefix_dir/lib"
cp -f src/lua.h src/luaconf.h src/lualib.h src/lauxlib.h src/lua.hpp "$prefix_dir/include/"
cp -f src/liblua.a "$prefix_dir/lib/"

# make pc only generates a partial pkg-config file; pass INSTALL_TOP so paths are correct
mkdir -p "$prefix_dir/lib/pkgconfig"
make INSTALL_TOP="$prefix_dir" pc >"$prefix_dir/lib/pkgconfig/lua.pc"
cat >>"$prefix_dir/lib/pkgconfig/lua.pc" <<'EOF'
Name: Lua
Description:
Version: ${version}
Libs: -L${libdir} -llua
Cflags: -I${includedir}
EOF
