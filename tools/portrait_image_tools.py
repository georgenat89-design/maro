"""Image sampling and landmark constants shared by the offline mesh fitter."""
import numpy as np

OVAL=[10,338,297,332,284,251,389,356,454,323,361,288,397,365,379,378,
      400,377,152,148,176,149,150,136,172,58,132,93,234,127,162,21,54,103,67,109]

def bilinear(image,x,y):
    x=np.clip(x,0,image.shape[1]-1.001);y=np.clip(y,0,image.shape[0]-1.001)
    ix,iy=x.astype(int),y.astype(int)
    fx,fy=(x-ix)[...,None],(y-iy)[...,None]
    return ((image[iy,ix]*(1-fx)+image[iy,ix+1]*fx)*(1-fy)
            +(image[iy+1,ix]*(1-fx)+image[iy+1,ix+1]*fx)*fy)
