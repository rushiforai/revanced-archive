#import "ValencedCoreBridge.h"

#include <stdlib.h>
#include <string.h>

NSString *ValencedCore_ConsumeString(char *value) {
    if (!value) return @"";
    NSString *string = [NSString stringWithUTF8String:value] ?: @"";
    ValencedCore_FreeString(value);
    return string;
}

NSString *ValencedCore_ConsumeStringOrNil(char *value) {
    if (!value) return nil;
    NSString *string = [NSString stringWithUTF8String:value];
    ValencedCore_FreeString(value);
    return string;
}

NSString *ValencedCore_ConsumeError(char *value) {
    NSString *message = ValencedCore_ConsumeStringOrNil(value);
    return message.length > 0 ? message : @"Unknown ValencedCore error";
}

char *ValencedCore_CopyCString(NSString *value) {
    if (value == nil) return NULL;
    const char *utf8 = value.UTF8String;
    if (utf8 == NULL) return NULL;
    size_t length = strlen(utf8);
    char *copy = malloc(length + 1);
    if (copy == NULL) return NULL;
    memcpy(copy, utf8, length + 1);
    return copy;
}
