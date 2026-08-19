#include <stdio.h>
#include <string.h>
#include <stddef.h>
#include <time.h>
#include <stdarg.h>

#include "lbLog.h"

static LbLogLevel_e logLevel = LBLOG_LEVEL_DEBUG;

void lbSetLogLevel (LbLogLevel_e level)
{
    if (level > LBLOG_LEVEL_NOLOG || level < LBLOG_LEVEL_DEBUG ) {
        return ;
    }
    logLevel = level;
}
#ifndef ANDROID

int addTimestamp(char *buf, int buf_size)
{
    int ret = 0;
    time_t timestamp = time(NULL);
    struct tm result;

    if (localtime_r(&timestamp, &result) != NULL) {
        ret = snprintf(buf, buf_size, "%d-%02d-%02d %02d:%02d:%02d",
               result.tm_year + 1900, result.tm_mon + 1, result.tm_mday,
               result.tm_hour, result.tm_min, result.tm_sec);
    } else {
        printf("Failed to convert timestamp to local time.\n");
    }
    return ret >= buf_size ? -1 : ret;
}

void logDebug(char *modulename, char *format,...)
{
    if (logLevel > LBLOG_LEVEL_DEBUG) return ;
    char timestamp[64] = {0};
    if (addTimestamp(timestamp, sizeof(timestamp))) {
        printf("D/%s/%s: ", timestamp, modulename);
    } else {
        printf("D/%s: ", modulename);
    }

    va_list valist;
    va_start(valist, format); 
    vprintf(format, valist);  
    va_end(valist);
    if (NULL == strchr(format, '\n'))
    {
        printf("\n");
    }
}

void logWarn(char *modulename, char *format,...)
{
    if (logLevel > LBLOG_LEVEL_WARN) return ;
    char timestamp[64] = {0};
    if (addTimestamp(timestamp, sizeof(timestamp))) {
        printf("W/%s/%s: ", timestamp, modulename);
    } else {
        printf("W/%s: ", modulename);
    }
    va_list valist;
    va_start(valist, format); 
    vprintf(format, valist); 
    va_end(valist);
    if (NULL == strchr(format, '\n'))
    {
        printf("\n");
    }
}

void logInfo(char *modulename, char *format,...)
{
    if (logLevel > LBLOG_LEVEL_INFO) return ;
    char timestamp[64] = {0};
    if (addTimestamp(timestamp, sizeof(timestamp))) {
        printf("I/%s/%s: ", timestamp, modulename);
    } else {
        printf("I/%s: ", modulename);
    }
    va_list valist;
    va_start(valist, format); 
    vprintf(format, valist); 
    va_end(valist);
    if (NULL == strchr(format, '\n'))
    {
        printf("\n");
    }
}

void logError(char *modulename, char *format,...)
{
    if (logLevel > LBLOG_LEVEL_ERROR) return ;
    char timestamp[64] = {0};
    if (addTimestamp(timestamp, sizeof(timestamp))) {
        printf("E/%s/%s: ", timestamp, modulename);
    } else {
        printf("E/%s: ", modulename);
    }
    va_list valist;
    va_start(valist, format); 
    vprintf(format, valist); 
    va_end(valist);
    if (NULL == strchr(format, '\n'))
    {
        printf("\n");
    }
}


#endif

