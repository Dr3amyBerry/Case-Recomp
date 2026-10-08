"""Control/capture one Windows process window without global cursor or focus changes."""
import argparse
import ctypes as C
from ctypes import wintypes as W
import json
from pathlib import Path

class RECT(C.Structure):
    _fields_ = [(n, W.LONG) for n in ('left', 'top', 'right', 'bottom')]
class POINT(C.Structure):
    _fields_ = [('x', W.LONG), ('y', W.LONG)]
class BITMAPINFOHEADER(C.Structure):
    _fields_ = [('size', W.DWORD), ('width', W.LONG), ('height', W.LONG),
                ('planes', W.WORD), ('bits', W.WORD), ('compression', W.DWORD),
                ('image_size', W.DWORD), ('xppm', W.LONG), ('yppm', W.LONG),
                ('used', W.DWORD), ('important', W.DWORD)]
class BITMAPINFO(C.Structure):
    _fields_ = [('header', BITMAPINFOHEADER), ('colors', W.DWORD * 3)]

u = C.WinDLL('user32', use_last_error=True)
g = C.WinDLL('gdi32', use_last_error=True)
callback = C.WINFUNCTYPE(W.BOOL, W.HWND, W.LPARAM)
u.EnumWindows.argtypes = [callback, W.LPARAM]
u.GetWindowThreadProcessId.argtypes = [W.HWND, C.POINTER(W.DWORD)]
u.IsWindowVisible.argtypes = [W.HWND]
u.IsIconic.argtypes = [W.HWND]
u.GetWindowTextLengthW.argtypes = [W.HWND]
u.GetWindowTextW.argtypes = [W.HWND, W.LPWSTR, C.c_int]
u.GetClientRect.argtypes = [W.HWND, C.POINTER(RECT)]
u.GetForegroundWindow.restype = W.HWND
u.GetCursorPos.argtypes = [C.POINTER(POINT)]
u.PostMessageW.argtypes = [W.HWND, W.UINT, W.WPARAM, W.LPARAM]
u.PostMessageW.restype = W.BOOL
u.GetDC.argtypes = [W.HWND]; u.GetDC.restype = W.HDC
u.ReleaseDC.argtypes = [W.HWND, W.HDC]
u.PrintWindow.argtypes = [W.HWND, W.HDC, W.UINT]
u.PrintWindow.restype = W.BOOL
g.CreateCompatibleDC.argtypes = [W.HDC]; g.CreateCompatibleDC.restype = W.HDC
g.CreateDIBSection.argtypes = [W.HDC, C.POINTER(BITMAPINFO), W.UINT, C.POINTER(C.c_void_p), W.HANDLE, W.DWORD]
g.CreateDIBSection.restype = W.HBITMAP
g.SelectObject.argtypes = [W.HDC, W.HANDLE]; g.SelectObject.restype = W.HANDLE
g.DeleteObject.argtypes = [W.HANDLE]; g.DeleteDC.argtypes = [W.HDC]

def find_window(pid):
    windows = []
    @callback
    def visit(hwnd, _):
        owner = W.DWORD(); u.GetWindowThreadProcessId(hwnd, C.byref(owner))
        if owner.value == pid and u.IsWindowVisible(hwnd) and u.GetWindowTextLengthW(hwnd):
            windows.append(hwnd)
        return True
    if not u.EnumWindows(visit, 0): raise C.WinError(C.get_last_error())
    if len(windows) != 1: raise RuntimeError(f'expected one visible process window, got {len(windows)}')
    return windows[0]

def state():
    p = POINT(); u.GetCursorPos(C.byref(p))
    return {'foreground':u.GetForegroundWindow(), 'cursor':[p.x,p.y]}

def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('pid', type=int); p.add_argument('action', choices=['inspect','capture','move','click'])
    p.add_argument('--x', type=int); p.add_argument('--y', type=int); p.add_argument('--output')
    a = p.parse_args(); hwnd = find_window(a.pid)
    if u.IsIconic(hwnd): raise RuntimeError('target is minimized; restore it manually before capture/input')
    rect = RECT()
    if not u.GetClientRect(hwnd,C.byref(rect)): raise C.WinError(C.get_last_error())
    width,height = rect.right,rect.bottom
    if not (0 < width <= 8192 and 0 < height <= 8192 and width*height <= 16_777_216): raise RuntimeError('invalid window size')
    title = C.create_unicode_buffer(u.GetWindowTextLengthW(hwnd)+1); u.GetWindowTextW(hwnd,title,len(title))
    before=state()
    if a.action in ('move','click'):
        if a.x is None or a.y is None or not (0<=a.x<width and 0<=a.y<height): raise ValueError('coordinates must be inside client area')
        coords = (a.y << 16) | a.x
        def post(msg,buttons=0):
            if not u.PostMessageW(hwnd,msg,buttons,coords): raise C.WinError(C.get_last_error())
        post(0x200)
        if a.action=='click':
            post(0x201,1); post(0x202)
    if a.action=='capture':
        if not a.output: raise ValueError('capture requires --output')
        from PIL import Image
        dc=u.GetDC(hwnd); mem=g.CreateCompatibleDC(dc); bitmap=None; old=None
        try:
            info=BITMAPINFO(); h=info.header; h.size=C.sizeof(BITMAPINFOHEADER); h.width=width; h.height=-height; h.planes=1; h.bits=32
            bits=C.c_void_p(); bitmap=g.CreateDIBSection(dc,C.byref(info),0,C.byref(bits),None,0)
            if not bitmap or not bits.value: raise C.WinError(C.get_last_error())
            old=g.SelectObject(mem,bitmap)
            if not u.PrintWindow(hwnd,mem,3): raise RuntimeError('PrintWindow unsupported by target')
            pixels=C.string_at(bits,width*height*4)
            im=Image.frombytes('RGB',(width,height),pixels,'raw','BGRX')
            out=Path(a.output);out.parent.mkdir(parents=True,exist_ok=True);im.save(out)
        finally:
            if old:g.SelectObject(mem,old)
            if bitmap:g.DeleteObject(bitmap)
            if mem:g.DeleteDC(mem)
            if dc:u.ReleaseDC(hwnd,dc)
    after=state()
    print(json.dumps({'pid':a.pid,'hwnd':hwnd,'title':title.value,'client':[width,height], 'action':a.action,'before':before,'after':after,'sameFocus':before['foreground']==after['foreground'],'sameCursor':before['cursor']==after['cursor']}))

if __name__=='__main__':main()
