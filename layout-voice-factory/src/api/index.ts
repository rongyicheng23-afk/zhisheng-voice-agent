import axios from 'axios';

const trimTrailingSlash = (value: string) => value.replace(/\/+$/, '');

const resolveRuntimeHttpBaseUrl = () => {
  const configuredBaseUrl = (process.env.VUE_APP_API_BASE_URL || '').trim();
  if (configuredBaseUrl) {
    return trimTrailingSlash(configuredBaseUrl);
  }

  if (typeof window === 'undefined') {
    return '';
  }

  const { hostname, port, origin } = window.location;
  const isLocalPreview = (hostname === 'localhost' || hostname === '127.0.0.1') && port === '8081';
  if (isLocalPreview) {
    return 'http://localhost:18080';
  }

  return trimTrailingSlash(origin);
};

export const getRuntimeHttpBaseUrl = () => resolveRuntimeHttpBaseUrl();

export const getRuntimeWsBaseUrl = () => {
  const configuredWsBaseUrl = (process.env.VUE_APP_WS_BASE_URL || '').trim();
  if (configuredWsBaseUrl) {
    return trimTrailingSlash(configuredWsBaseUrl);
  }

  if (typeof window === 'undefined') {
    return '';
  }

  const { hostname, port, protocol, host } = window.location;
  const isLocalPreview = (hostname === 'localhost' || hostname === '127.0.0.1') && port === '8081';
  if (isLocalPreview) {
    return 'ws://localhost:18080';
  }

  const wsProtocol = protocol === 'https:' ? 'wss:' : 'ws:';
  return `${wsProtocol}//${host}`;
};

/**
 * The realtime orchestration gateway is deliberately separate from Spring
 * Boot. During local development it listens on 18081; deployed environments
 * should inject its public WSS base URL instead of relying on a port number.
 */
export const getRealtimeGatewayWsBaseUrl = () => {
  const configuredGatewayUrl = (process.env.VUE_APP_REALTIME_GATEWAY_WS_BASE_URL || '').trim();
  if (configuredGatewayUrl) {
    return trimTrailingSlash(configuredGatewayUrl);
  }

  if (typeof window === 'undefined') {
    return '';
  }

  const { hostname, port, protocol, host } = window.location;
  const isLocalPreview = (hostname === 'localhost' || hostname === '127.0.0.1') && port === '8081';
  if (isLocalPreview) {
    return 'ws://localhost:18081';
  }

  const wsProtocol = protocol === 'https:' ? 'wss:' : 'ws:';
  return `${wsProtocol}//${host}`;
};

const http = axios.create({
  baseURL: resolveRuntimeHttpBaseUrl(), // 本地开发直连 18080，公网部署自动走当前域名
  withCredentials: false,
});

export const setAuthToken = (token?: string) => {
  if (token) {
    http.defaults.headers.common.Authorization = `Bearer ${token}`;
  } else {
    delete http.defaults.headers.common.Authorization;
  }
};

const initialToken = localStorage.getItem('token') || sessionStorage.getItem('token');
setAuthToken(initialToken || undefined);

// 请求拦截器
http.interceptors.request.use(
  config => {
    const token = localStorage.getItem('token') || sessionStorage.getItem('token');
    if (token) {
      config.headers = config.headers || {};
      config.headers.Authorization = `Bearer ${token}`;
    } else if (config.headers) {
      delete config.headers.Authorization;
    }
    return config;
  },
  error => {
    // 请求错误处理
    return Promise.reject(error);
  }
);
 
// 响应拦截器
http.interceptors.response.use(
  response => {
    const res = response.data;

    // 兼容语音工厂原有服务返回
    if (res?.status === "success") {
      return res;
    }

    // 兼容 springboot-minio 的认证返回
    if (typeof res?.code === 'number') {
      return res;
    }

    return Promise.reject({
      message: 'Error',
      status: res?.status,
    });
  },
  error => {
    // 对响应错误做处理
    return Promise.reject(error);
  }
);
export default http;
