

#ifndef H_LB_LOG
#define H_LB_LOG

#include "lb.h"


typedef enum __lb_log_level__
{
    LBLOG_LEVEL_DEBUG = 0,
    LBLOG_LEVEL_INFO = 1,
    LBLOG_LEVEL_WARN = 2,
    LBLOG_LEVEL_ERROR = 3,
    LBLOG_LEVEL_NOLOG,
} LbLogLevel_e;

#ifndef ANDROID

void fvSetLogLevel (LbLogLevel_e level);
void logDebug(char *modulename, char *format,...);
void logWarn(char *modulename, char *format,...);
void logInfo(char *modulename, char *format,...);
void logError(char *modulename, char *format,...);

#else

#include <android/log.h>
#define logDebug(TAG, fmt, args...)  __android_log_print(ANDROID_LOG_DEBUG, TAG, fmt,  ##args)
#define logInfo(TAG, fmt, args...)  __android_log_print(ANDROID_LOG_INFO, TAG, fmt, ##args)
#define logWarn(TAG, fmt, args...)  __android_log_print(ANDROID_LOG_WARN, TAG, fmt,  ##args)
#define logError(TAG, fmt, args...)  __android_log_print(ANDROID_LOG_ERROR, TAG, fmt, ##args)
#endif


#endif


