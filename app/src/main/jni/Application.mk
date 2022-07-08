APP_CFLAGS     := -Wall -Wextra -Wmost
APP_CFLAGS     += -fno-stack-protector -fomit-frame-pointer
APP_CPPFLAGS   := -std=c++20
APP_LDFLAGS    := -Wl,--gc-sections
APP_STL        := c++_static

ifneq ($(NDK_DEBUG),1)
APP_CFLAGS     += -Ofast -flto -Wno-unused-parameter -Werror
APP_CFLAGS     += -fvisibility=hidden -fvisibility-inlines-hidden
APP_CFLAGS     += -fno-unwind-tables -fno-asynchronous-unwind-tables
APP_LDFLAGS    += -flto -Wl,--exclude-libs,ALL -Wl,--strip-all
endif
