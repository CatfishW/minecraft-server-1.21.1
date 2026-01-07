# -*- coding: utf-8 -*-

from .type import RequestType


def ecommerce_applyment_submit(self, out_request_no, organization_type, business_license_info, organization_cert_info, id_doc_type, id_card_info,
                               id_doc_info, need_account_info, account_info, contact_info, sales_scene_info, merchant_shortname, qualifications,
                               business_addition_pics, business_addition_desc):
    """提交申请单
    :param business_code: 业务申请编号，示例值：'APPLYMENT_00000000001'
    :param contact_info: 超级管理员信息，示例值：{'contact_name':'张三','contact_id_number':'320311770706001','mobile_phone':'13900000000','contact_email':'admin@demo.com'}
    :param subject_info: 主体资料，示例值：{'subject_type':'SUBJECT_TYPE_ENTERPRISE','business_license_info':{'license_copy':'demo-media-id','license_number':'123456789012345678','merchant_name':'腾讯科技有限公司','legal_person':'张三'},'identity_info':{'id_doc_type':'IDENTIFICATION_TYPE_IDCARD','id_card_info'{'id_card_copy':'demo-media-id'}}}
    :param business_info: 经营资料，示例值：{'merchant_shortname':'张三餐饮店','service_phone':'0758xxxxxx','sales_info':{'sales_scenes_type':['SALES_SCENES_STORE','SALES_SCENES_MP']}}
    :param settlement_info: 结算规则，示例值：{'settlement_id':'719','qualification_type':'餐饮'}
    :param bank_account_info: 结算银行账户，示例值：{'bank_account_type':'BANK_ACCOUNT_TYPE_CORPORATE','account_name':'xx公司','account_bank':'工商银行','bank_address_code':'110000','account_number':'1234567890'}
    :param addition_info: 补充材料，示例值：{'legal_person_commitment':'demo-media-id'}
    """
    params = {}
    if business_code:
        params.update({'business_code': business_code})
    else:
        raise Exception('business_code is not assigned.')
    if contact_info:
        params.update({'contact_info': contact_info})
    else:
        raise Exception('contact_info is not assigned.')
    if subject_info:
        params.update({'subject_info': subject_info})
    else:
        raise Exception('subject_info is not assigned.')
    if business_info:
        params.update({'business_info': business_info})
    else:
        raise Exception('business_info is not assigned')
    if settlement_info:
        params.update({'settlement_info': settlement_info})
    else:
        raise Exception('settlement_info is not assigned.')
    if bank_account_info:
        params.update({'bank_account_info': bank_account_info})
    else:
        raise Exception('bank_account_info is not assigned.')
    if addition_info:
        params.update({'addition_info': addition_info})
    cipher_data = False
    if params.get('contact_info').get('contact_name'):
        params['contact_info']['contact_name'] = self._core.encrypt(params['contact_info']['contact_name'])
        cipher_data = True
    if params.get('contact_info').get('contact_id_number'):
        params['contact_info']['contact_id_number'] = self._core.encrypt(params['contact_info']['contact_id_number'])
        cipher_data = True
    if params.get('contact_info').get('mobile_phone'):
        params['contact_info']['mobile_phone'] = self._core.encrypt(params['contact_info']['mobile_phone'])
        cipher_data = True
    if params.get('contact_info').get('contact_email'):
        params['contact_info']['contact_email'] = self._core.encrypt(params['contact_info']['contact_email'])
        cipher_data = True
    if params.get('bank_account_info').get('account_name'):
        params['bank_account_info']['account_name'] = self._core.encrypt(params['bank_account_info']['account_name'])
        cipher_data = True
    if params.get('bank_account_info').get('account_number'):
        params['bank_account_info']['account_number'] = self._core.encrypt(params['bank_account_info']['account_number'])
        cipher_data = True
    path = '/v3/applyment4sub/applyment'
    return self._core.request(path, method=RequestType.POST, data=params, cipher_data=cipher_data)
