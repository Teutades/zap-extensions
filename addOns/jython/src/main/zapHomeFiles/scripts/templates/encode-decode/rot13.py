import string
from org.zaproxy.addon.encoder.processors import EncodeDecodeResult

def process(helper, value):
    try:
        value = value.encode('ascii')
    except UnicodeEncodeError:
        return EncodeDecodeResult("There are non-ascii characters in the input.")
    rot13 = string.maketrans( 
    "ABCDEFGHIJKLMabcdefghijklmNOPQRSTUVWXYZnopqrstuvwxyz", 
    "NOPQRSTUVWXYZnopqrstuvwxyzABCDEFGHIJKLMabcdefghijklm")
    return helper.newResult(string.translate(value, rot13))
