#ifndef H_LB_API
#define H_LB_API

/*
    模块名称：
    Loopback module

    模块功能：
    该模块监听一个udp端口用于接收sip消息
    
    1.sip注册消息：
    通过判断和mqtt服务连接状态来回复注册成功或失败

    2.invite消息：
	收到sip invite，通过mqtt回调发送，如果发送失败，回复sip消息结束通话
	收到mqtt invite，通过回环地址发送到sip模块
	
    3.其他sip消息，直接转发。
*/

#ifdef __cplusplus
extern "C"
{
#endif

typedef enum
{
    SIP_REGISTER_SUCCESS = 200,
    SIP_REGISTER_ERROR_403 = 403,
    SIP_REGISTER_ERROR_404 = 404,
    SIP_REGISTER_ERROR_408 = 408,
}sipRegisterType_e;

typedef enum {
    SIP_METHOD_REGISTER = 0,
    SIP_METHOD_INVITE,
    SIP_METHOD_BYE,
    SIP_METHOD_CANCEL,
    SIP_METHOD_OTHRE,
} lbSipMethod_e;

typedef struct lbSipHeader_t
{
    lbSipMethod_e method;
    char from[128];
    char to[128];
    char callID[128];
    int code;
    int isRequest;
} lbSipHeader_t;

typedef struct lbMqttCB_t
{
    /**
    * @brief mqtt连接状态检测
    * @return 连接成功返回0，未连接返回-1
    */
    int (*mqttLinkCheck)();

    /**
    * @brief 通过mqtt协议发送sip消息
    * @return 发送成功返回0，发送失败返回-1
    */
    int (*mqttSend)(char *data, lbSipHeader_t *sipHeader);
} lbMqttCB_t;

/**
* @brief 模块初始化
* @param listenPort 设置监听端口号
* @return 成功返回0，失败返回-1
*/
int lbInit(int listenPort);

/**
* @brief 模块注销
* @return 成功返回0，失败返回-1
*/
int lbUnInit();

/**
* @brief 注册mqtt回调
* @param cbs mqtt回调函数
* @return 成功返回0，失败返回-1
*/
int lbMqttCallBackRegister(lbMqttCB_t *cbs);

/**
* @brief Mqtt收到消息
* @param data sip消息
* @param dataLen sip消息长度
* @return 处理成功返回0，处理失败返回-1
*/
int lbMqttMsgIncoming(char *data, int dataLen);

#ifdef __cplusplus
}
#endif

#endif








