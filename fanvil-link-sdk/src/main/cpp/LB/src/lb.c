#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <pthread.h>
#include <arpa/inet.h>
#include <unistd.h>

#include "lb.h"
#include "lbLog.h"


typedef struct
{
    int fd;
    int listenPort;
    int peerPort;
    pthread_t thread;
    int running;
    lbMqttCB_t cb;
} lbInfo_t;

static char *lbLogTag = "lblog";
static lbInfo_t gLB = {0};

static int createSocket(int port)
{
    struct sockaddr_in ipAddr;
    gLB.fd = socket(AF_INET, SOCK_DGRAM, 0);
    ipAddr.sin_family = AF_INET;
    ipAddr.sin_port = htons(port);
    ipAddr.sin_addr.s_addr = htonl(INADDR_ANY);
    if (bind(gLB.fd, (struct sockaddr *)&ipAddr, sizeof(ipAddr)) < 0) {
        close(gLB.fd);
        logError(lbLogTag, "%s bind rtp err(%d)", __FUNCTION__, port);
        return -1;
    }
    gLB.listenPort = port;
    return 0;
}

static lbSipMethod_e getSipMethod(char *sipMsg)
{
    if (strncmp(sipMsg, "REGISTER", strlen("REGISTER")) == 0) {
        return SIP_METHOD_REGISTER;
    }
    if (strncmp(sipMsg, "INVITE", strlen("INVITE")) == 0) {
        return SIP_METHOD_INVITE;
    }
    if (strncmp(sipMsg, "CANCEL", strlen("CANCEL")) == 0) {
        return SIP_METHOD_CANCEL;
    }
    if (strncmp(sipMsg, "BYE", strlen("BYE")) == 0) {
        return SIP_METHOD_BYE;
    }
    return SIP_METHOD_OTHRE;
}

static int isRequestSipMsg(char *sipMsg)
{
    if (strncmp(sipMsg, "SIP/2.0", strlen("SIP/2.0")) == 0) {
        return 0;
    } else {
        return 1;
    }
}

static int getSipFrom(char *sipMsg, char *from, int len)
{
    char line[256] = {0};
    char *lineBegin = strstr(sipMsg, "From:");
    if (!lineBegin) return -1;
    char *lineEnd = strstr(lineBegin, "\r\n");
    if (!lineEnd) return -1;
    if ((lineEnd-lineBegin < 0) || (lineEnd-lineBegin > sizeof(line))) return -1;
    strncpy(line, lineBegin, lineEnd-lineBegin);
    char *fromBegin = strstr(line, "sip:");
    if (!fromBegin) return -1;
    fromBegin = fromBegin+strlen("sip:");
    char *fromEnd = strstr(fromBegin, "@");
    if (!fromEnd) return -1;
    if ((fromEnd-fromBegin < 0) || (fromEnd-fromBegin > len)) return -1;
    strncpy(from, fromBegin, fromEnd-fromBegin);
    return 0;
}

static int getSipTo(char *sipMsg, char *to, int len)
{
    char line[256] = {0};
    char *lineBegin = strstr(sipMsg, "To:");
    if (!lineBegin) return -1;
    char *lineEnd = strstr(lineBegin, "\r\n");
    if (!lineEnd) return -1;
    if ((lineEnd-lineBegin < 0) || (lineEnd-lineBegin > sizeof(line))) return -1;
    strncpy(line, lineBegin, lineEnd-lineBegin);
    char *toBegin = strstr(line, "sip:");
    if (!toBegin) return -1;
    toBegin = toBegin+strlen("sip:");
    char *toEnd = strstr(toBegin, "@");
    if (!toEnd) return -1;
    if ((toEnd-toBegin < 0) || (toEnd-toBegin > len)) return -1;
    strncpy(to, toBegin, toEnd-toBegin);
    return 0;
}

static int getSipCallId(char *sipMsg, char *callId, int len)
{
    char line[512] = {0};
    char *lineBegin = strstr(sipMsg, "Call-ID:");
    if (!lineBegin) return -1;
    char *lineEnd = strstr(lineBegin, "\r\n");
    if (!lineEnd) return -1;
    if ((lineEnd-lineBegin < 0) || (lineEnd-lineBegin > sizeof(line))) return -1;
    strncpy(line, lineBegin, lineEnd-lineBegin);
    char *callIdBegin = line+strlen("Call-ID:")+1;
    if (strlen(callIdBegin) > len) return -1;
    strncpy(callId, callIdBegin, strlen(callIdBegin));
    return 0;
}

static int getSipResponseCode(char *sipMsg)
{
    int code = 0;
    sscanf(sipMsg, "SIP/2.0 %d", &code);
    return code;
}

static int lbSipCopyLine(char *req, char *res, char *key)
{
    char *start, *end;
    int len = 0;
    start = strstr(req, key);
    if (start == NULL) {
        return -1;
    }
    end = strstr(start, "\r\n");
    if (end == NULL) {
        return -1;
    }
    len = end - start + 2;
    strncpy(res, start, len);
    return 0;
}

static int lbSipCopyVia(char *req, char *res)
{
    char *start, *end, *rport;
    int len = 0;
    start = strstr(req, "Via:");
    if (start == NULL) {
        return -1;
    }
    end = strstr(start, "\r\n");
    if (end == NULL) {
        return -1;
    }
    len = end - start;
    strncpy(res, start, len);
    rport = strstr(start, "\r\n");
    if (rport) {
        char tmp[16] = {0};
        snprintf(tmp, sizeof(tmp), "=%d", gLB.listenPort);
        strcat(res, tmp);
    }
    strcat(res, "\r\n");
    return 0;
}

static int lbSipRegCopyContact(char *req, char *res, int isConnect)
{
    if (isConnect) {
        lbSipCopyLine(req, res, "Contact:");
    } else {
        char *begin, *end;
        char contact[128] = {0};
        begin = strstr(req, "Contact:");
        if (!begin) return -1;
        end = strstr(begin, "\r\n");
        if (!end) return -1;

        strncpy(contact, begin, end-begin);
        strcat(contact, ";expires=60");
        strcat(res, contact);
        strcat(res, "\r\n");
    }
    return 0;
}

static int lbGenSipInviteErrResp(char *req, char *res)
{
    strcpy(res, "SIP/2.0 487 Request Terminated");
    strcat(res, "\r\n");
    lbSipCopyVia(req, res+strlen(res));
    lbSipCopyLine(req, res+strlen(res), "From:");
    lbSipCopyLine(req, res+strlen(res), "To:");
    lbSipCopyLine(req, res+strlen(res), "Call-ID:");
    lbSipCopyLine(req, res+strlen(res), "CSeq:");
    lbSipCopyLine(req, res+strlen(res), "Contact:");
    lbSipCopyLine(req, res+strlen(res), "Allow:");
    lbSipCopyLine(req, res+strlen(res), "Content-Length:");
    strcat(res, "\r\n");
    return 0;
}

static int lbGenSipRegResp(char *req, char *res)
{
    int isConnect = 0;
    sipRegisterType_e ret = SIP_REGISTER_SUCCESS;
    // if (!gLB.cb.mqttLinkCheck || gLB.cb.mqttLinkCheck() < 0) {
    //     strcpy(res, "SIP/2.0 403 forbidden");
    //     isConnect = 0;
    // } else {
    //     isConnect = 1;
    //     strcpy(res, "SIP/2.0 200 OK");
    // }

    if (gLB.cb.mqttLinkCheck)
    {
        ret = gLB.cb.mqttLinkCheck();
        // printf("[%s][%d]ret = %d\n",__FUNCTION__,__LINE__,ret);
        if(SIP_REGISTER_ERROR_403 == ret)
        {
            isConnect = 0;
            strcpy(res, "SIP/2.0 403 forbidden");
        }
        else if(SIP_REGISTER_ERROR_404 == ret)
        {
            isConnect = 0;
            strcpy(res, "SIP/2.0 404 not found");
        }
        else if(SIP_REGISTER_ERROR_408 == ret)
        {
            isConnect = 0;
            strcpy(res, "SIP/2.0 408 request timeout");
        }
        else
        {
            isConnect = 1;
            strcpy(res, "SIP/2.0 200 OK");
        }
    }
    else 
    {
        strcpy(res, "SIP/2.0 403 forbidden");
        isConnect = 0;
    }

    strcat(res, "\r\n");
    lbSipCopyVia(req, res+strlen(res));
    lbSipCopyLine(req, res+strlen(res), "From:");
    lbSipCopyLine(req, res+strlen(res), "To:");
    lbSipCopyLine(req, res+strlen(res), "Call-ID:");
    lbSipCopyLine(req, res+strlen(res), "CSeq:");
    lbSipRegCopyContact(req, res+strlen(res), isConnect);
    //lbSipCopyLine(req, res+strlen(res), "Contact:");
    lbSipCopyLine(req, res+strlen(res), "Allow:");
    lbSipCopyLine(req, res+strlen(res), "Content-Length:");
    strcat(res, "\r\n");
    return 0;
}

static int lbSipMsgSend(char *data, int len)
{
    struct sockaddr_in destAddr;
	destAddr.sin_family = AF_INET;
	destAddr.sin_port = htons(gLB.peerPort);
	destAddr.sin_addr.s_addr = inet_addr("127.0.0.1");
    //destAddr.sin_addr.s_addr = inet_addr("172.16.18.142");
	sendto(gLB.fd, data, len, 0, (struct sockaddr*)&destAddr, sizeof(destAddr));
    //logInfo(lbLogTag, "%s sendto 127.0.0.1:%d len:%d\n{%s} \n\n", __FUNCTION__, gLB.peerPort, len, data);
    return 0;
}

int lbGetSipHeader(char *sipMsg, lbSipHeader_t *header)
{
    memset(header, 0, sizeof(lbSipHeader_t));
    getSipCallId(sipMsg, header->callID, sizeof(header->callID));
    header->method = getSipMethod(sipMsg);
    header->isRequest = isRequestSipMsg(sipMsg);
    if (header->isRequest) {
        getSipFrom(sipMsg, header->from, sizeof(header->from));
        getSipTo(sipMsg, header->to, sizeof(header->to));
    } else {
        getSipFrom(sipMsg, header->to, sizeof(header->to));
        getSipTo(sipMsg, header->from, sizeof(header->from));
        header->code = getSipResponseCode(sipMsg);
    }
    // logInfo(lbLogTag, "%s callid:%s, method:%d, from:%s, to:%s, code:%d isRequest:%d\n", __FUNCTION__, 
    //     header->callID, header->method, header->from, header->to, header->code, header->isRequest);
    return 0;
}

static int lbSipRegisterProc(char *data)
{
    char resp[1024] = {0};
    lbGenSipRegResp(data, resp);
    lbSipMsgSend(resp, strlen(resp));
    return 0;
}

static int lbSipInviteProc(char *data)
{
    int ret;
    lbSipHeader_t header;
    lbGetSipHeader(data, &header);
    if (gLB.cb.mqttSend) {
        ret = gLB.cb.mqttSend(data, &header);
    }
    if (ret < 0) {
        char resp[1024] = {0};
        lbGenSipInviteErrResp(data, resp);
        lbSipMsgSend(resp, strlen(resp));
    }
    return 0;
}

static int lbSipOthreProc(char *data)
{
    lbSipHeader_t header;
    lbGetSipHeader(data, &header);
    if (gLB.cb.mqttSend) {
        gLB.cb.mqttSend(data, &header);
    }
    return 0;
}

static int lbRecvDataProc(char *data, int len)
{
    lbSipMethod_e method;
    if (len <= 8) {
        return 0;
    }
    method = getSipMethod(data);
    switch (method)
    {
        case SIP_METHOD_REGISTER:
            lbSipRegisterProc(data);
            break;
        case SIP_METHOD_INVITE:
            lbSipInviteProc(data);
            break;
        default:
            lbSipOthreProc(data);
            break;
    }
    return 0;
}

static void *lbRecvThread(void *arg)
{
    gLB.running = 1;
    while (gLB.running) {
        int num;
        struct timeval timeout;
        fd_set _readFds;
        FD_ZERO(&_readFds);
        FD_SET(gLB.fd, &_readFds);
        timeout.tv_sec = 0;
        timeout.tv_usec = 100*1000;//100ms
        char buff[2048] = {0};
        int recvLen = 0;
        struct sockaddr_in peerAddr;
        int peerAddrLen = sizeof(struct sockaddr_in);
        num = select(gLB.fd+1, &_readFds, NULL, NULL, &timeout);
        if (num <= 0) {
            continue;
        }

        recvLen = recvfrom(gLB.fd, buff, sizeof(buff), 0, (struct sockaddr *)&peerAddr, &peerAddrLen);
        gLB.peerPort = ntohs(peerAddr.sin_port);
        //logDebug(lbLogTag, "%s:%d recvLen:%d \n{%s}\n", __FUNCTION__, __LINE__, recvLen, buff);
        if (recvLen > 0) {
            lbRecvDataProc(buff, recvLen);
        }
    }
    close(gLB.fd);
    // logInfo(lbLogTag, "%s thread exit.", __FUNCTION__);
    return NULL;
}

int lbInit(int listenPort)
{
    if(1 == gLB.running)
    {
        return 0;
    }
    // logDebug(lbLogTag, "%s:%d\n", __FUNCTION__, __LINE__);
    if (createSocket(listenPort) < 0) {
        return -1;
    }

    pthread_create(&gLB.thread, NULL, lbRecvThread, NULL);
    return 0;
}

int lbUnInit()
{
    gLB.running = 0;
    gLB.peerPort = 0;
    return 0;
}

int lbMqttCallBackRegister(lbMqttCB_t *cbs)
{
    if (cbs) {
        memcpy(&gLB.cb, cbs, sizeof(lbMqttCB_t));
    }
    return 0;
}

int lbMqttMsgIncoming(char *data, int dataLen)
{
    if (!data || dataLen <= 0 || gLB.peerPort <= 0) {
        return -1;
    }
    lbSipMsgSend(data, dataLen);
    return 0;
}