#pragma once

#import <Foundation/Foundation.h>

#import "ValencedCore.h"

NS_ASSUME_NONNULL_BEGIN

#ifdef __cplusplus
extern "C" {
#endif

NSString *ValencedCore_ConsumeString(char * _Nullable value);

NSString * _Nullable ValencedCore_ConsumeStringOrNil(char * _Nullable value);

NSString *ValencedCore_ConsumeError(char * _Nullable value);

char * _Nullable ValencedCore_CopyCString(NSString * _Nullable value);

#ifdef __cplusplus
}
#endif

NS_ASSUME_NONNULL_END
