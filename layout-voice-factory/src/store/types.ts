export interface UserProps {
  username?: string;
  nickName?: string;
  _id?: string;
  email?: string;
  description?: string;
  avatar?: ImageProps;
  isLogin?: boolean;
}

export interface ImageProps {
  _id?: string;
  url?: string;
  fitUrl?: string;
  createdAt?: string;
}




export interface RuleProps {
  type: 'required' | 'email' | 'custom';
  message: string;
  validator?: () => boolean;
}

export type MessageType = 'success' | 'error' | 'default'

export interface ResponseType<P = {}> {
  code: number;
  msg: string;
  data: P;
}

export interface GlobalErrorProps {
  status: boolean;
  message?: string;
}

interface ListProps<P> {
  [id: string]: P;
}


export interface GlobalDataProps {
  token: string;
  error: GlobalErrorProps;
  loading: boolean;
  user: UserProps;
}
